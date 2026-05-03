# kotpb-grpc-codegen (pure-Kotlin reimplementation of `protoc-gen-grpc-kotlin`)

A protoc plugin (`protoc-gen-grpc-kotlin`) that generates Kotlin coroutine gRPC
client stubs and server bases. Compatible with the existing
`io.grpc:grpc-kotlin-stub` runtime, but unlike the upstream plugin, the generated
code does **not** depend on `protoc-gen-grpc-java` output. Only `protoc-gen-java`
(for messages) is required.

Supports proto2, proto3, edition 2023, and edition 2024.

## Contents

- [Quick start (consumer)](#quick-start-consumer)
- [Runtime requirements](#runtime-requirements)
- [Modules](#modules)
- [Build & test](#build--test)
- [Plugin options](#plugin-options) — `--grpc-kotlin_opt=...`
- [Proto file options honored](#proto-file-options-honored) — `option ... = ...;`
- [Generated code shape](#generated-code-shape)
- [File-splitting](#file-splitting)
- [Service descriptor accessors](#service-descriptor-accessors)
- [Editions support](#editions-support)
- [JVM-less native binary (GraalVM)](#jvm-less-native-binary-graalvm)
- [Maven Central publishing (TODO)](#maven-central-publishing-todo-for-the-maintainer)

## Quick start (consumer)

Once the artifacts are published to Maven Central (or any Maven repo your build
already resolves from), wire the plugin via `protobuf-gradle-plugin`:

```kotlin
plugins {
    id("com.google.protobuf")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:<version>" }
    plugins {
        id("grpckt") {
            artifact = "io.github.kotpb:kotpb-grpc-codegen:<version>"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins { id("grpckt") }
        }
    }
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:<version>")
    implementation("io.grpc:grpc-kotlin-stub:<version>")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:<version>")
    // pick a transport, e.g. io.grpc:grpc-netty-shaded
}
```

`protobuf-gradle-plugin` auto-resolves the matching native classifier for your
host OS / arch. **No JVM is required at codegen time on the consumer side.**

## Runtime requirements

What a consumer project compiling and running the generated Kotlin code needs.

### JVM

The generated source contains no JVM-level construct that demands a
specific bytecode floor — no method handles, records, sealed-permits,
`invokedynamic`-only features, etc. So **the codegen itself imposes no
JVM minimum.**

### Kotlin

The generator emits code compatible with Kotlin 1.0–1.1 language features (`suspend`,
`companion object`, `@JvmStatic`, `by lazy`, default-value parameters,
top-level `object`, single-interface class delegation).
Except for streaming services that require `kotlinx.coroutines.flow.Flow` in that case
**language-level floor from the generated code is Kotlin 1.3** (when `Flow` was introduced).

### Library versions

What versions we test against in `:e2e-tests`, plus the minimum versions
that should still resolve every symbol the generated code imports.

| Artifact                                               | Tested with | Compatible since | Earliest symbol our code imports                                                     |
| ------------------------------------------------------ | ----------- | ---------------- | ------------------------------------------------------------------------------------ |
| `io.grpc:grpc-api`                                     | 1.81.0      | 1.26.0           | `Channel`, `CallOptions`, `MethodDescriptor`, … (predate the API split)              |
| `io.grpc:grpc-protobuf`                                | 1.81.0      | 1.0              | `ProtoUtils.marshaller`, `ProtoFileDescriptorSupplier`                               |
| `io.grpc:grpc-protobuf-lite` _(only when `lite=true`)_ | 1.81.0      | 1.0              | `ProtoLiteUtils.marshaller`                                                          |
| `io.grpc:grpc-kotlin-stub`                             | 1.4.3       | 1.0.0 (2020)     | `AbstractCoroutineStub`, `AbstractCoroutineServerImpl`, `ClientCalls`, `ServerCalls` |
| `com.google.protobuf:protobuf-java`                    | 4.34.1      | 3.x              | `Descriptors.FileDescriptor`, `<Message>.getDefaultInstance()`                       |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core`        | 1.10.2      | 1.3.0            | `kotlinx.coroutines.flow.Flow`                                                       |

## Modules

- **`:generator`** — codegen library + its unit-test source set. Reads
  `CodeGeneratorRequest`, emits Kotlin via KotlinPoet.
- **`:plugin`** — produces `protoc-gen-grpc-kotlin`. A thin `main()` over
  `:generator` plus the `application` plugin (for the JVM dist), the GraalVM
  native-image plugin (for native binaries), and `maven-publish` (for the
  classifier-per-platform Maven publication).
- **`:e2e-tests`** — invokes `protoc` with `protoc-gen-java` and our plugin
  against fixtures covering every (syntax × `java_multiple_files`)
  combination, then runs in-process gRPC client/server tests for unary,
  server-streaming, client-streaming, and bidi RPCs. Includes wire-level
  interop tests against hand-built `MethodDescriptor`s.
- **`buildSrc/`** — convention plugin (`kotpb.kotlin-conventions`) applied to
  every module (Kotlin/JVM 17 toolchain via foojay, JUnit 5 platform,
  reproducible jars, consistent test logging) plus the `DownloadHyperfineTask`
  used by `:benchmark`.
- **`:benchmark`** — opt-in build-time benchmark comparing this plugin (native
  + JVM) to the upstream `protoc-gen-grpc-java` + `protoc-gen-grpc-kotlin`
  pipeline via hyperfine.

## Build & test

```powershell
./gradlew build                   # compile + run every test (103 total)
./gradlew :generator:test         # generator unit tests
./gradlew :e2e-tests:test         # protoc + RPC tests
./gradlew :plugin:installDist     # produce the JVM protoc-gen-grpc-kotlin
./gradlew :plugin:nativeCompile   # produce the native binary (needs GraalVM
                                  # toolchain; auto-downloaded by foojay)
```

The JVM executable lands at
`plugin/build/install/protoc-gen-grpc-kotlin/bin/protoc-gen-grpc-kotlin[.bat]`.
The native binary lands at
`plugin/build/native/nativeCompile/protoc-gen-grpc-kotlin[.exe]`.

## Plugin options

Pass via protoc's `--grpc-kotlin_opt=...` flag (comma-separated to combine,
e.g. `--grpc-kotlin_opt=comments,java_package=com.acme`).

| Option                          | Effect                                                                                                                                                                                                |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `lite` (or `lite=true`)         | Emit `ProtoLiteUtils.marshaller(...)` instead of `ProtoUtils.marshaller(...)`. See note below.                                                                                                        |
| `comments` (or `comments=true`) | Preserve `.proto` source leading comments as KDoc on the outer object, the stub class, the impl base, the `MethodDescriptor` properties, and every per-method client/server function. Off by default. |
| `java_package=<pkg>`            | Override the Kotlin output package. Otherwise resolved from the file's `option java_package`, falling back to the proto `package`.                                                                    |

### When to use `lite`

This is a small but real distinction. At runtime, `ProtoUtils.marshaller(T)` in
`io.grpc:grpc-protobuf` _delegates straight to_ `ProtoLiteUtils.marshaller(T)` in
`io.grpc:grpc-protobuf-lite` — same marshaller object, only the type bound and
the _import_ differ:

- Default (`lite` unset): generated code imports
  `io.grpc.protobuf.ProtoUtils`, which requires `T : com.google.protobuf.Message`
  (full, descriptor-bearing messages from `protoc-gen-java`). The consumer must
  depend on `io.grpc:grpc-protobuf`.
- `lite=true`: generated code imports `io.grpc.protobuf.lite.ProtoLiteUtils`,
  which accepts `T : com.google.protobuf.MessageLite` (lite messages from
  `protoc-gen-javalite` and full messages alike — `Message extends MessageLite`).
  The consumer can depend only on the smaller `io.grpc:grpc-protobuf-lite`.

So pick `lite=true` exactly when your messages come from `protoc-gen-javalite`
and you want to drop the `grpc-protobuf` dependency. For everyone else, the
default is correct.

## Proto file options honored

Standard protobuf file/service/method options that affect codegen:

| Option                                       | Effect                                                                                                                                                                                                                                                                                                              |
| -------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `option java_package = "...";`               | Used as the Kotlin output package. Plugin option `--grpc-kotlin_opt=java_package=...` overrides it; otherwise the proto `package` is the fallback.                                                                                                                                                                  |
| `option java_outer_classname = "...";`       | The Java class containing `getDescriptor()` for the proto file. Our descriptor suppliers reference it. If unset, derived from the filename (`my_proto.proto` → `MyProto`); if that derivation collides with a top-level message/enum/service, `OuterClass` is appended (matching `protoc-gen-java`'s rule exactly). |
| `option java_multiple_files = true;`         | Emit one Kotlin file per service: `<Service>GrpcKt.kt`. Otherwise every service in the `.proto` is bundled into `<OuterClass>GrpcKt.kt`. **Removed in editions 2024**, where multi-file output is the default.                                                                                                      |
| `option deprecated = true;` (on a `service`) | Emits `@Deprecated("This service is deprecated.")` on `<Service>CoroutineStub` and `<Service>CoroutineImplBase`.                                                                                                                                                                                                    |
| `option deprecated = true;` (on an `rpc`)    | Emits `@Deprecated("This RPC is deprecated.")` on the client stub fn, the server impl-base fn, and the `MethodDescriptor` property. `bindService()` gets `@Suppress("DEPRECATION")` because it must reference deprecated methods internally.                                                                        |

What the plugin does **not** consume from `.proto`:

- Field-level features, types, `oneof`, enums, etc. — those are `protoc-gen-java`'s domain.
- `option go_package`, `option csharp_namespace`, language-specific options other than the `java_*` ones above.
- Custom options.

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
        : AbstractCoroutineStub<FooCoroutineStub>(channel, callOptions) {

        companion object { @JvmStatic val serviceDescriptor: ServiceDescriptor }

        suspend fun unary(request: ReqT, headers: Metadata = Metadata()): RespT
        fun serverStream(request: ReqT, headers: Metadata = Metadata()): Flow<RespT>
        suspend fun clientStream(requests: Flow<ReqT>, headers: Metadata = Metadata()): RespT
        fun bidiStream(requests: Flow<ReqT>, headers: Metadata = Metadata()): Flow<RespT>
    }

    abstract class FooCoroutineImplBase(coroutineContext: CoroutineContext = EmptyCoroutineContext)
        : AbstractCoroutineServerImpl(coroutineContext), BindableService {

        companion object { @JvmStatic val serviceDescriptor: ServiceDescriptor }

        open suspend fun unary(request: ReqT): RespT          // throws UNIMPLEMENTED by default
        open fun serverStream(request: ReqT): Flow<RespT>     // throws UNIMPLEMENTED by default
        open suspend fun clientStream(requests: Flow<ReqT>): RespT
        open fun bidiStream(requests: Flow<ReqT>): Flow<RespT>

        final override fun bindService(): ServerServiceDefinition
    }
}
```

## File-splitting

Output mirrors `protoc-gen-java`'s behaviour:

- **`option java_multiple_files = true`** (or any edition 2024+ proto, where
  it's the default): one Kotlin file per service, named `<Service>GrpcKt.kt`.
- **`java_multiple_files` unset / false**: every service in the `.proto` is
  bundled into a single file named after the proto's outer class:
  `<OuterClass>GrpcKt.kt`.

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
output is the default — the generator handles this automatically when
resolving message Java class names and file-splitting.

## JVM-less native binary (GraalVM)

The plugin compiles to a self-contained native binary that runs without a JVM
installed. Useful for distributing as a single executable or for protoc
invocations on machines that don't have Java set up.

### Locally

```powershell
./gradlew :plugin:nativeCompile
```

GraalVM JDK 21 itself is downloaded automatically by Gradle's foojay
toolchain resolver — no separate install required. Per-platform OS toolchain
prerequisites still apply:

| Platform | Needs                                                                                            |
| -------- | ------------------------------------------------------------------------------------------------ |
| Linux    | `gcc`, `glibc` headers (preinstalled on most distros)                                            |
| macOS    | Xcode command-line tools (`xcode-select --install`)                                              |
| Windows  | Visual Studio 2022 with the C++ build tools, or run inside the _x64 Native Tools Command Prompt_ |

### Cross-platform binaries via CI

`.github/workflows/native-binaries.yml` runs on every push of a `v*` tag and
builds binaries for five targets, attaching them to the matching GitHub
Release. The same workflow can be triggered manually from the Actions tab.

| Classifier       | Runner             | Linkage             |
| ---------------- | ------------------ | ------------------- |
| `linux-x86_64`   | `ubuntu-latest`    | static (musl)       |
| `linux-aarch_64` | `ubuntu-24.04-arm` | static (musl)       |
| `osx-x86_64`     | `macos-13`         | dynamic (libSystem) |
| `osx-aarch_64`   | `macos-latest`     | dynamic (libSystem) |
| `windows-x86_64` | `windows-latest`   | dynamic (msvcrt)    |

Both Linux binaries are statically linked against musl (`--static
--libc=musl`), so they run without glibc version surprises and work inside
minimal containers (alpine, distroless). macOS and Windows are dynamically
linked against the platform's system C library — that's the only practical
option there.

Each produced binary is **smoke-tested in CI** before upload by generating
the same `.proto` through both the native binary and the JVM-mode plugin and
asserting **byte-identical output**. KotlinPoet's emission is deterministic
(no timestamps, stable descriptor iteration), so any divergence is a genuine
native-image regression caused by reflection, class initialization, or
resource-lookup behavior. A binary that compiled but produces different
output than the JVM build fails the workflow and is never published.

### Build-flag rationale

| Flag                                        | Why                                                                                                                                                                                           |
| ------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `--no-fallback`                             | Fail loudly instead of silently producing a slow JIT-fallback binary if reflection metadata is missing.                                                                                       |
| `--strict-image-heap`                       | Future-default class-init mode; surfaces accidental run-time heap pollution at build time.                                                                                                    |
| `--link-at-build-time=io.github.kotpb`      | Forces our own classes to fully link at build time; tiny startup win and catches missing-class errors at build. Scoped to our group so library deps still init at runtime where they need to. |
| `-O3`                                       | Speed-of-execution tier; the plugin runs once per protoc build so runtime savings compound.                                                                                                   |
| `-march=compatibility`                      | Broad CPU-arch compatibility (never `-march=native` since we ship the binary).                                                                                                                |
| `-R:MaxHeapSize=128m`                       | Cap at 128 MiB; the default is 80 % of physical RAM, wasteful for a one-shot CLI.                                                                                                             |
| `-H:+ReportExceptionStackTraces`            | Better diagnostics if reflection-shaped issues surface.                                                                                                                                       |

### Consuming the native binary as a protoc plugin

The native binaries are published with **classifier-per-platform** Maven
naming, the same shape `protoc-gen-grpc-java` uses, so they're consumable
directly from `protobuf-gradle-plugin`:

```kotlin
protobuf {
    plugins {
        id("grpckt") {
            artifact = "io.github.kotpb:kotpb-grpc-codegen:<version>"
        }
    }
}
```

`protobuf-gradle-plugin` auto-resolves the matching classifier for the host
OS / arch and runs the binary as a `--plugin=` to protoc — no JVM required
on the consumer side.

For local integration testing before a release is published:

```bash
./gradlew :plugin:nativeCompile
./gradlew :plugin:publishNativeBinaryPublicationToMavenLocal \
    -PnativeBinaryFile=plugin/build/native/nativeCompile/protoc-gen-grpc-kotlin \
    -PnativeBinaryClassifier=linux-x86_64    # adjust per host
```

Consumer build adds `mavenLocal()` to repositories.

## Maven Central publishing (TODO for the maintainer)

The Gradle publication skeleton is in place — `:plugin` produces a
`nativeBinary` Maven publication with the classifier-per-platform shape
described above, and the CI workflow uploads each platform's staging
directory as an artifact. To turn that into actual Maven Central releases
you need to:

1. Claim the `io.github.kotpb` namespace on Sonatype Central (or change
   the `groupId` in `:plugin/build.gradle.kts` to a namespace you already own).
2. Add a GPG signing key (Maven Central requires `.asc` signatures on every
   artifact).
3. Apply `io.github.gradle-nexus.publish-plugin` in `settings.gradle.kts` and
   add a publish job to `.github/workflows/native-binaries.yml` that
   downloads all `maven-staging-*` artifacts and runs
   `:closeAndReleaseStagingRepository` with credentials in
   `${{ secrets.OSSRH_USERNAME }}` / `${{ secrets.OSSRH_PASSWORD }}` /
   `${{ secrets.SIGNING_KEY }}`.

Until then, releases land as binary attachments on the GitHub Release page.
