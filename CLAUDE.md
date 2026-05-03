# kotpb-grpc-codegen — project context

Pure-Kotlin reimplementation of `protoc-gen-grpc-kotlin`, published as
`io.github.kotpb:kotpb-grpc-codegen` under the [Kotpb](https://github.com/Kotpb)
GitHub org. Emits gRPC stubs + impl bases that run against
`io.grpc:grpc-kotlin-stub` unchanged, but the generated code is
**self-contained**: no `protoc-gen-grpc-java` (`*Grpc` classes) needed — only
`protoc-gen-java` for messages. The native binary keeps the canonical name
`protoc-gen-grpc-kotlin` so consumers' `--grpc-kotlin_out=` invocations are
drop-in compatible.

## Module layout

- `:generator` — codegen library + its unit-test source set. KotlinPoet does
  the emission. Entry point: `GeneratorRunner.runOnStdio()`. Per-section
  generators (`ServiceNameGenerator`, `ClientStubGenerator`, …) all follow
  the same shape: `apply(builder: TypeSpec.Builder, ctx: ServiceContext)`,
  orchestrated by `ProtoFileCodeGenerator`. In-memory unit-test fixtures
  live in `TestFixtures.kt` (build `CodeGeneratorRequest` programmatically).
- `:plugin` — application module. Produces:
  - JVM dist via `installDist`
  - native binary via `:plugin:nativeCompile` (GraalVM)
  - Maven publication (`nativeBinary`) with classifier-per-platform
- `:e2e-tests` — real protoc + in-process gRPC tests. **Folder names are
  self-documenting** (`proto3_multifiles/`, `editions2024/`, etc.) — keep
  them that way; folder name == proto file name == proto package suffix.
- `buildSrc/` — convention plugin `kotpb.kotlin-conventions` plus the
  custom `DownloadHyperfineTask` used by `:benchmark`.

## Load-bearing design invariants (do not drift from these)

1. **Self-contained generated code.** Never reference any `*Grpc` class
   (those are `protoc-gen-grpc-java` output). `NoGrpcJavaReferenceTest`
   locks this in. Our suppliers + descriptors are emitted inline in our
   own output.
2. **File-splitting mirrors `protoc-gen-java`** byte-for-byte:
   `java_multiple_files=true` (or edition 2024 default) → one
   `<Service>GrpcKt.kt` per service; otherwise bundled into
   `<OuterClass>GrpcKt.kt`.
3. **Outer-class collision rule must match `protoc-gen-java` exactly.**
   When the derived outer class name collides with a top-level
   message/enum/service, append `OuterClass`. Tested by
   `outer_class_collision/` e2e fixture — if our derivation diverges
   from `protoc-gen-java`'s, that test fails to compile.
4. **The generator imposes no JVM or Kotlin-language floor of its own.**
   The floors are entirely from libraries the consumer pulls in.

## Common commands

```powershell
./gradlew build                                   # full build + every test
./gradlew :generator:test                         # generator unit tests
./gradlew :e2e-tests:test                         # protoc + RPC
./gradlew :e2e-tests:test --tests *NameCollisionsTest*   # narrow to one fixture
./gradlew :plugin:installDist                     # JVM dist
./gradlew :plugin:nativeCompile                   # native binary (needs GraalVM)
./gradlew :plugin:publishNativeBinaryPublicationToMavenLocal `
    -PnativeBinaryFile=plugin/build/native/nativeCompile/protoc-gen-grpc-kotlin `
    -PnativeBinaryClassifier=linux-x86_64         # local Maven install for consumer testing
./gradlew :plugin:tasks --all                     # see all native + publish tasks
```

## Plugin options (`--grpc-kotlin_opt=...`)

- `lite=true` — emit `ProtoLiteUtils.marshaller(...)` instead of `ProtoUtils`.
  Real distinction is dependency hygiene (drop `grpc-protobuf`, keep only
  `grpc-protobuf-lite`); at runtime the two delegate to the same marshaller.
- `comments=true` — preserve `.proto` leading comments as KDoc.
- `java_package=<pkg>` — override Kotlin output package.

## Proto file options honored

- `option java_package` (Kotlin output package; plugin `java_package=` overrides; proto `package` fallback)
- `option java_outer_classname` (Java outer class for descriptor lookup; derived from filename if unset; `OuterClass` collision rule)
- `option java_multiple_files` (file-splitting; removed in edition 2024 where multi-files is default)
- `option deprecated = true` on `service` or `rpc` — emits `@Deprecated`

## Conventions

- **Maven Central version lookup**: always read `repo1.maven.org/.../maven-metadata.xml`
  directly. The `search.maven.org` Solr API systematically under-reports newer
  versions (it has hidden Kotlin 2.3.x and JUnit 6.x from us twice).
- **Renames**: use `git mv` so history stays intact.
- **Folder/test naming**: e2e fixtures self-describe what they test
  (`proto3_bundled/`, `name_collisions/`, …). Don't reintroduce neutral names
  like `echo/`, `greet/`.
- **Cross-platform line endings**: KotlinPoet emits `\n` and `.gitattributes`
  enforces LF on `.kt` files in repo. The CI native-binary smoke test
  `diff`s native vs JVM output byte-for-byte and relies on this.

## Gotchas

- **`libs.versions.<x>.get()` does NOT compile inside `protobuf{}` blocks.**
  Persistent Kotlin DSL accessor quirk. Use `libs.<library>.get().toString()`
  instead. See `e2e-tests/build.gradle.kts`.
- **`protoc` rejects an `rpc` whose name matches a message name** in the same
  scope (relative-type resolution gets confused). Workaround in fixture
  `.proto`s: absolute qualifiers like `.collision.Collision`.
- **`java_multiple_files = true` is removed in edition 2024.** Setting it
  there is a protoc error. Our `DescriptorUtil.isJavaMultipleFiles` knows.
- **GraalVM external Reachability Metadata Repository** has a schema that
  the foojay-provided `graalvm-community 21.0.2` can't read. Keep the
  in-Gradle `metadataRepository.enabled = false`. protobuf-java embeds its
  own metadata so we don't need the external repo.
- **Native binary on Windows** needs Visual Studio MSVC. CI uses
  `ilammy/msvc-dev-cmd@v1`; locally users need Build Tools.

## Out of scope (don't add)

- **allOpen / open stubs / `extra_class_annotations`** (issues #473 / #230 /
  #601). User explicitly said "Do nothing" when offered. Keep stubs `final`.
- **Message-side concerns** (issue #745 — proto3 optional nullability).
  That's `protoc-gen-java`'s domain.
- **Runtime back-pressure fixes** (issue #581). Lives in
  `io.grpc:grpc-kotlin-stub`, not in our codegen.

## Native binary distribution

`.github/workflows/native-binaries.yml` builds 5 classifier-per-platform
binaries on every `v*` tag (or workflow_dispatch):

- `linux-x86_64` and `linux-aarch_64` — musl-static (no glibc dependency)
- `osx-x86_64`, `osx-aarch_64`
- `windows-x86_64`

Smoke test in CI: native binary's output is `diff`'d byte-for-byte against
the JVM dist's output for the same `.proto`. Any divergence is a
native-image regression and the workflow fails before upload. Maven Central
publishing is wired but not active — needs maintainer to claim the
`io.github.kotpb` namespace and add OSSRH/GPG secrets.
