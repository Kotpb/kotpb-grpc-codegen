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
./gradlew build                       # compile everything
./gradlew :generator-tests:test       # generator unit tests
./gradlew :e2e-tests:test             # full e2e + wire interop
./gradlew :plugin:installDist         # produce the protoc-gen-grpc-kotlin executable
```

The plugin executable lands at
`plugin/build/install/protoc-gen-grpc-kotlin/bin/protoc-gen-grpc-kotlin[.bat]`.

## Plugin options

Pass via protoc's `--grpc-kotlin_opt=...` flag (comma-separated to combine):

- `lite` (or `lite=true`) — emit `ProtoLiteUtils.marshaller(...)` instead of
  `ProtoUtils.marshaller(...)` for use with protobuf-javalite.
- `java_package=<pkg>` — override the Kotlin output package (otherwise resolved
  from the file's `java_package` option, falling back to the proto package).
- `comments` (or `comments=true`) — preserve `.proto` source comments as KDoc on
  the generated stub class, server impl base, and per-method declarations
  (client function, server function, and `MethodDescriptor` property).

## Generated code shape

For each `service Foo` in a `.proto`:

```kotlin
object FooGrpcKt {
    const val SERVICE_NAME: String = "<protoPackage>.Foo"

    val getXxxMethod: MethodDescriptor<RequestT, ResponseT> by lazy { /* self-built */ }
    val serviceDescriptor: ServiceDescriptor by lazy { /* self-built */ }

    private abstract class FooBaseDescriptorSupplier : ProtoFileDescriptorSupplier, ProtoServiceDescriptorSupplier
    private class FooFileDescriptorSupplier : FooBaseDescriptorSupplier()
    private class FooMethodDescriptorSupplier(...) : FooBaseDescriptorSupplier(), ProtoMethodDescriptorSupplier

    class FooCoroutineStub(channel: Channel, callOptions: CallOptions = CallOptions.DEFAULT)
        : AbstractCoroutineStub<FooCoroutineStub>(channel, callOptions) { ... }

    abstract class FooCoroutineImplBase(coroutineContext: CoroutineContext = EmptyCoroutineContext)
        : AbstractCoroutineServerImpl(coroutineContext), BindableService { ... }
}
```

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
