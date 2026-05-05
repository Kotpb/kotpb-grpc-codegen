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
./gradlew :plugin:installDist                     # JVM dist (launcher script + lib jars)
./gradlew :plugin:shadowJar                       # JVM fat-jar (single file, classifier=jvm)
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

`.github/workflows/native-binaries.yml` builds 5 classifiers under one
publication on every `v*` tag (or workflow_dispatch) — 4 native + 1 JVM:

- `linux-x86_64` (`.exe`) — musl-static (no glibc dependency, runs on alpine/distroless)
- `linux-aarch_64` (`.exe`) — mostly-static (`-H:+StaticExecutableWithDynamicLibC`):
  GraalVM runtime statically linked, libc stays dynamic against glibc.
  Runs on every mainstream aarch64 distro and `gcr.io/distroless/base`,
  but **not** alpine:aarch64. GraalVM CE doesn't ship static musl libs
  for linux-aarch64 (oracle/graal#4645, #10018 — both closed "not planned"),
  so musl-static aarch64 isn't achievable with our current toolchain.
- `osx-aarch_64` (`.exe`) — dynamic against libSystem (Apple Silicon)
- `windows-x86_64` (`.exe`) — dynamic against msvcrt
- `jvm` (`.jar`) — shaded fat-JAR via the `com.gradleup.shadow` plugin's
  `shadowJar` task. `Main-Class: io.github.kotpb.plugin.MainKt` set, all
  runtime deps shaded inside, POM declares zero dependencies.
  `protobuf-gradle-plugin` recognises `@jar` on a classifier'd artifact
  and wraps it with `java -jar`, so consumers consume it identically to
  the native classifiers — `artifact = "...:jvm@jar"`. Built once on the
  linux-x86_64 runner since the JAR's contents are platform-independent;
  uploaded as the `kotpb-grpc-codegen-jvm` artifact and merged into
  `native-binaries/` alongside the .exe files at publish time. Mirrors
  upstream's `io.grpc:protoc-gen-grpc-kotlin:VERSION:jdk8@jar` shape.

No `osx-x86_64` classifier: GitHub-hosted `macos-13` (Intel) runners are
being phased out (`macos-12` retired Dec 2024). Intel Mac users — rare in
2026 since Apple has shipped only Apple Silicon since 2020 — use the
`jvm` classifier instead.

## Branch protection

`main` is protected. **Direct pushes are blocked** — every change must
land via a PR + squash-merge. The protection rules:

- Pull request required (0 approvals — solo merger is fine).
- Status checks required: `build + test (linux-x86_64)` (the CI build),
  `lint` (the Conventional-Commits PR-title check).
- Branches must be up-to-date with main before merging (`strict: true`).
- Linear history enforced (squash-merge already does this; this is the
  belt-and-braces lock).
- Force pushes blocked. Branch deletion blocked.
- Conversation resolution required before merge.
- Admins are NOT enforced (`enforce_admins: false`) — admins can disable
  protection temporarily for emergencies, but must re-enable.

Configured via `gh api -X PUT repos/Kotpb/kotpb-grpc-codegen/branches/main/protection`.

## Release process

Versioning is automated by **release-please** (Google) driven by
**Conventional Commits** in PR titles. Squash-merge is the only allowed
merge mode, so each PR title becomes the squashed commit subject.

### What lands a version bump

| PR title prefix | Version bump | CHANGELOG section |
|---|---|---|
| `feat: ...` | minor | Features |
| `fix: ...` / `perf: ...` / `revert: ...` | patch | Bug Fixes / Performance / Reverts |
| `feat!: ...` or `BREAKING CHANGE:` in body | major | ⚠ BREAKING CHANGES |
| `docs:`, `ci:`, `build:` | none | Visible in changelog |
| `refactor:`, `test:`, `chore:` | none | Hidden from changelog |

`.github/workflows/lint-pr-title.yml` blocks PRs whose title doesn't
match — the merge button is the manual gate, the lint is its
prerequisite.

### How a release happens

1. PRs land on `main` with Conventional-Commits titles.
2. `.github/workflows/release-please.yml` runs after every push and
   maintains a **release PR** (`chore(main): release vX.Y.Z`) showing
   the proposed version, the CHANGELOG.md diff, and the bumped
   `gradle.properties`.
3. Maintainer reviews the release PR and **clicks merge** to release —
   that's the manual trigger.
4. release-please tags `vX.Y.Z` and creates the GitHub Release.
5. The tag push fires `.github/workflows/native-binaries.yml`:
   - `build` matrix produces the 4 classifier-per-platform binaries
     plus the `jvm` shadow JAR (built only on linux-x86_64)
   - `release` job attaches them to the GitHub Release
   - `publish-maven-central` job downloads all 5, runs ONE Gradle
     publish (aggregate-mode via `-PnativeBinariesDir=...`) so the
     gradle-nexus-publish-plugin batches them into a single Sonatype
     Central deployment, then `closeAndReleaseSonatypeStagingRepository`
     finalizes.
6. Within ~5 min, artifacts are visible at
   `https://central.sonatype.com/artifact/io.github.kotpb/kotpb-grpc-codegen`.

### One-time maintainer setup (do before the first release)

These steps gate the Maven Central side; the GitHub Release side works
without them.

1. **Claim the namespace** at <https://central.sonatype.com> →
   "Add Namespace" → `io.github.kotpb`. Auto-verified via the GitHub
   OAuth proof when the org name matches.
2. **Generate a GPG key** for signing:
   ```sh
   gpg --full-generate-key      # RSA 4096, no expiry or 2y
   gpg --list-secret-keys --keyid-format=long
   gpg --armor --export-secret-keys <KEY-ID>     # → SIGNING_KEY value
   gpg --keyserver keys.openpgp.org --send-keys <KEY-ID>
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY-ID>
   ```
3. **Generate a Sonatype Central user token**:
   central.sonatype.com → account dropdown → "View Account" →
   "Generate User Token" → record `username` + `password`.
4. **Set GitHub repo secrets**:
   ```sh
   gh secret set SONATYPE_USERNAME --repo Kotpb/kotpb-grpc-codegen
   gh secret set SONATYPE_PASSWORD --repo Kotpb/kotpb-grpc-codegen
   gh secret set SIGNING_KEY      --repo Kotpb/kotpb-grpc-codegen
   gh secret set SIGNING_PASSWORD --repo Kotpb/kotpb-grpc-codegen
   ```

The `SIGNING_KEY` value is the entire `-----BEGIN PGP PRIVATE KEY BLOCK-----`
block produced by `gpg --armor --export-secret-keys`. Without these
secrets, the publish job would still attempt to run on tag-push and fail —
either gate the workflow on a `vars.MAVEN_CENTRAL_READY == 'true'` repo
variable or just don't push tags until secrets exist.

### Local smoke test

```powershell
./gradlew :plugin:nativeCompile :plugin:shadowJar
./gradlew :plugin:publishNativeBinaryPublicationToMavenLocal `
    -PnativeBinaryFile="$pwd/plugin/build/native/nativeCompile/protoc-gen-grpc-kotlin.exe" `
    -PnativeBinaryClassifier=windows-x86_64 `
    -PjvmJarFile="$pwd/plugin/build/libs/kotpb-grpc-codegen-<version>-jvm.jar"
ls ~/.m2/repository/io/github/kotpb/kotpb-grpc-codegen/<version>/
# expect: kotpb-grpc-codegen-<version>.pom
#         kotpb-grpc-codegen-<version>-windows-x86_64.exe
#         kotpb-grpc-codegen-<version>-jvm.jar
#         (.asc files only when SIGNING_KEY is set)
```

### Aggregate-mode publishing flow (CI only)

The publish-maven-central job downloads every classifier .exe into
`native-binaries/`, then:

```sh
./gradlew :plugin:publishNativeBinaryPublicationToSonatypeRepository \
          closeAndReleaseSonatypeStagingRepository \
          -PnativeBinariesDir="$PWD/native-binaries" --no-daemon
```

`-PnativeBinariesDir=...` switches the publishing block from
"single classifier" mode (used by `mavenLocal` smoke) to "aggregate"
mode where all 5 classifiers (4 native + jvm jar) share one
`MavenPublication` and therefore one Sonatype Central deployment.
See `plugin/build.gradle.kts:139-160`.

Smoke test in CI: native binary's output is `diff`'d byte-for-byte against
the JVM dist's output for the same `.proto`. Any divergence is a
native-image regression and the workflow fails before upload. Maven Central
publishing is wired but not active — needs maintainer to claim the
`io.github.kotpb` namespace and add OSSRH/GPG secrets.
