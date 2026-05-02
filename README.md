# grpc-kotlin compiler (pure-Kotlin reimplementation)

A protoc plugin (`protoc-gen-grpc-kotlin`) that generates Kotlin coroutine gRPC
client stubs and server bases. Compatible with the existing
`io.grpc:grpc-kotlin-stub` runtime, but unlike the upstream plugin, the generated
code does **not** depend on `protoc-gen-grpc-java` output. Only `protoc-gen-java`
(for messages) is required.

Supports proto2, proto3, edition 2023, and edition 2024.

## Modules

- **`:generator`** — The codegen library. Reads `CodeGeneratorRequest`, emits Kotlin
  via KotlinPoet.
- **`:plugin`** — Application module that produces the `protoc-gen-grpc-kotlin`
  executable (a thin `main()` over `:generator`).
- **`:generator-tests`** — Unit tests for the generator: descriptor → output shape,
  plugin options, and a negative test ensuring no `*Grpc` (grpc-java service) class
  is referenced.
- **`:e2e-tests`** — End-to-end tests: invokes protoc with `protoc-gen-java` and our
  plugin against fixtures (proto2, proto3, edition 2023, edition 2024), then runs
  in-process gRPC client/server tests for unary, server-streaming, client-streaming,
  and bidi RPCs. Includes wire-level interop tests against hand-built `MethodDescriptor`s.

## Build & test

```powershell
./gradlew build                  # compile everything + run tests
./gradlew :generator:test        # generator unit tests only
./gradlew :e2e-tests:test        # protoc + RPC tests only
./gradlew :plugin:installDist    # produce the JVM protoc-gen-grpc-kotlin
```

The JVM executable lands at
`plugin/build/install/protoc-gen-grpc-kotlin/bin/protoc-gen-grpc-kotlin[.bat]`.

## JVM-less native binary (GraalVM)

The plugin can be compiled to a self-contained native binary that runs
without a JVM installed. Useful for distributing as a single executable
or for protoc invocations on machines that don't have Java set up.

### Locally

```powershell
./gradlew :plugin:nativeCompile
```

The binary lands at
`plugin/build/native/nativeCompile/protoc-gen-grpc-kotlin[.exe]`.

Local prerequisites:

| Platform | Needs |
|---|---|
| Linux   | `gcc`, `glibc` headers (preinstalled on most distros) |
| macOS   | Xcode command-line tools (`xcode-select --install`) |
| Windows | Visual Studio 2022 with the C++ build tools, or run the build inside the *x64 Native Tools Command Prompt* |

GraalVM JDK 21 itself is downloaded automatically by Gradle's foojay
toolchain resolver — no separate install required.

### Cross-platform binaries via CI

`.github/workflows/native-binaries.yml` runs on every push of a `v*` tag
and builds binaries for **Linux x86_64**, **macOS x86_64**, **macOS
aarch64**, and **Windows x86_64**, attaching them to the corresponding
GitHub Release. The same workflow can be triggered manually from the
Actions tab to produce build artifacts without cutting a release.

## Plugin options

Pass via protoc's `--grpc-kotlin_opt=...` flag (comma-separated to combine):

- `lite` (or `lite=true`) — emit `ProtoLiteUtils.marshaller(...)` instead of
  `ProtoUtils.marshaller(...)` for use with protobuf-javalite.
- `java_package=<pkg>` — override the Kotlin output package (otherwise resolved
  from the file's `java_package` option, falling back to the proto package).
- `comments` (or `comments=true`) — preserve `.proto` source comments as KDoc on
  the generated stub class, server impl base, and per-method declarations
  (client function, server function, and `MethodDescriptor` property).

## Service descriptor accessors

The `ServiceDescriptor` is exposed in three places, all referring to the same
singleton instance:

- `<package>.<Service>GrpcKt.serviceDescriptor` — top-level on the outer object.
- `<Service>CoroutineStub.serviceDescriptor` — `@JvmStatic` companion accessor,
  reachable via the stub class alone (handy when reading service options
  reflectively from code that already imports the stub type).
- `<Service>CoroutineImplBase.serviceDescriptor` — same accessor on the impl
  base, plus `bindService()` already wires the descriptor into the
  `ServerServiceDefinition` it returns.

## Deprecation handling

`option deprecated = true` on a service or RPC produces matching `@Deprecated`
annotations in the Kotlin output:

- A deprecated **service** marks `<Service>CoroutineStub` and
  `<Service>CoroutineImplBase`.
- A deprecated **RPC** marks the client stub function, the server impl-base
  function, and the `MethodDescriptor` property for that method.
- The generated `bindService()` body, which has to reference deprecated methods
  internally, gets `@Suppress("DEPRECATION")` to keep the generated code
  compiling cleanly.

## Generated code shape

For each `service Foo` in a `.proto`:

```kotlin
object FooGrpcKt {
    const val SERVICE_NAME: String = "<protoPackage>.Foo"

    val getXxxMethod: MethodDescriptor<RequestT, ResponseT> by lazy { /* self-built */ }
    val serviceDescriptor: ServiceDescriptor by lazy { /* self-built */ }

    private object FooFileDescriptorSupplier : ProtoServiceDescriptorSupplier
    private class FooMethodDescriptorSupplier(...) : ProtoMethodDescriptorSupplier,
            ProtoServiceDescriptorSupplier by FooFileDescriptorSupplier

    class FooCoroutineStub(channel: Channel, callOptions: CallOptions = CallOptions.DEFAULT)
        : AbstractCoroutineStub<FooCoroutineStub>(channel, callOptions) { ... }

    abstract class FooCoroutineImplBase(coroutineContext: CoroutineContext = EmptyCoroutineContext)
        : AbstractCoroutineServerImpl(coroutineContext), BindableService { ... }
}
```

### File-splitting

Output mirrors `protoc-gen-java`'s behaviour:

- **`option java_multiple_files = true`** (or any edition 2024+ proto, where
  it's the default): one Kotlin file per service, named `<Service>GrpcKt.kt`.
- **`java_multiple_files` unset / false**: every service in the proto is
  bundled into a single file named after the proto's outer class:
  `<OuterClass>GrpcKt.kt`.

## Editions support

The plugin advertises editions support to protoc:

```
supported_features = FEATURE_PROTO3_OPTIONAL | FEATURE_SUPPORTS_EDITIONS
minimum_edition = EDITION_PROTO2
maximum_edition = EDITION_2024
```

Service codegen does not depend on field-level `FeatureSet` resolution; protoc
resolves features for the file/message/field descriptors before sending them.

In edition 2024, the `java_multiple_files` option was removed and multi-file
output is the default. The generator handles this automatically when resolving
message Java class names.
