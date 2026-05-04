plugins {
    id("kotpb.kotlin-conventions")
    application
    alias(libs.plugins.graalvm.native)
    alias(libs.plugins.shadow)
    `maven-publish`
    signing
}

application {
    mainClass.set("io.github.kotpb.plugin.MainKt")
    applicationName = "protoc-gen-grpc-kotlin"
}

dependencies {
    implementation(projects.generator)
}

// GraalVM native-image — produces a JVM-less `protoc-gen-grpc-kotlin` binary.
//
// Build with: ./gradlew :plugin:nativeCompile
// Output:     plugin/build/native/nativeCompile/protoc-gen-grpc-kotlin[.exe]
//
// Requires a GraalVM JDK on the toolchain. With our foojay-resolver setup
// Gradle auto-downloads it.
graalvmNative {
    toolchainDetection.set(true)

    binaries.named("main") {
        mainClass.set("io.github.kotpb.plugin.MainKt")
        imageName.set("protoc-gen-grpc-kotlin")
        buildArgs.addAll(
            // Fail loudly instead of silently producing a slow JIT-fallback
            // binary if reflection metadata is missing.
            "--no-fallback",

            // Future-default class-init mode; surfaces accidental run-time
            // heap pollution at build time. protobuf-java 4.x and KotlinPoet
            // both behave under it.
            "--strict-image-heap",

            // Force-link our own classes at build time. Tiny startup win and
            // catches missing-class errors at build instead of runtime.
            // Scope deliberately narrow so library deps (which may legitimately
            // initialize at run time) aren't affected.
            "--link-at-build-time=io.github.kotpb",

            // Optimize for size, not for runtime micro-wins. The plugin
            // runs ~1 second per protoc invocation, dominated by I/O and
            // one-shot KotlinPoet emission, so `-O3`'s extra inlining
            // bought almost nothing while bloating the binary 20-30%.
            // `-Os` is a GraalVM 23+ flag — see the `java-version` bump
            // to 25 in native-binaries.yml.
            "-Os",

            // Explicit "broad CPU compatibility" — never `native`, since the
            // produced binary is shipped to other machines.
            "-march=compatibility",

            // Cap runtime heap at 128 MiB. The default is 80% of physical RAM
            // — wasteful for a CLI invoked once per protoc build. Plenty of
            // headroom for KotlinPoet's intermediate buffers.
            "-R:MaxHeapSize=128m",

            // Better diagnostics if anything reflection-shaped breaks.
            "-H:+ReportExceptionStackTraces",

            // No-op GC: the plugin runs ~1s per protoc invocation and
            // exits, so nothing is ever freed anyway and the GC machinery
            // is dead weight in the binary. Heap is capped at 128 MiB
            // (-R:MaxHeapSize above) which is plenty for KotlinPoet's
            // intermediate buffers; if we ever exceeded it, we'd see
            // OOM rather than degraded perf — and that's the right
            // failure mode for a CLI plugin.
            "--gc=epsilon",
        )

        // Per-platform extras passed in by CI (e.g. `--static --libc=musl`
        // for the Linux runner so the binary is portable across glibc
        // versions and runnable inside alpine / distroless containers).
        val extraArgs = (project.findProperty("extraNativeBuildArgs") as? String)
            ?.split(' ')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (extraArgs.isNotEmpty()) buildArgs.addAll(extraArgs)

        // Use a GraalVM JDK 25 launcher specifically for native-image,
        // independent of the project's regular toolchain. JDK 25 is the
        // current GraalVM LTS and unlocks `-Os` (size-optimized native
        // image, available since GraalVM 23). Locally CI has the same
        // pin in `.github/workflows/native-binaries.yml`.
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(25))
                vendor.set(JvmVendorSpec.GRAAL_VM)
            }
        )
    }

    // The external GraalVM Reachability Metadata Repository requires a very
    // recent GraalVM schema. protobuf-java already embeds its own
    // META-INF/native-image/ metadata in the JAR (since v25, well before
    // the 4.34.x line we're on), and KotlinPoet does no reflection. So we
    // can rely on that and skip the external repo.
    metadataRepository {
        enabled.set(false)
    }
}

// Shadow JAR — JVM fallback for unsupported native classifiers.
//
// Build with: ./gradlew :plugin:shadowJar
// Output:     plugin/build/libs/kotpb-grpc-codegen-<version>-jvm.jar
//
// Self-contained: shades every runtime dep (KotlinPoet, kotlin-stdlib,
// kotlinx-coroutines, protobuf-java, …) into a single executable JAR so
// the published Maven POM doesn't have to declare any of them. The
// `application` plugin's main class is auto-picked up via the manifest's
// Main-Class attribute, so `java -jar <this>` runs the plugin directly —
// which is exactly what protobuf-gradle-plugin does when it sees `@jar`
// on the classifier'd artifact, mirroring how upstream
// `io.grpc:protoc-gen-grpc-kotlin:VERSION:jdk8@jar` works.
tasks.shadowJar {
    archiveBaseName.set("kotpb-grpc-codegen")
    archiveClassifier.set("jvm")
    // No archiveVersion override: defaults to project.version (correct).
    mergeServiceFiles()

    // Tree-shake unused classes from shaded deps. The codegen reaches a
    // tiny slice of protobuf-java + kotlinpoet + kotlin-stdlib; the rest
    // is dead weight. minimize() walks reachability from the Main-Class
    // and drops anything unreached. The CI smoke test (shadowJar output
    // == JVM dist output, byte-for-byte) catches over-minimization.
    minimize {
        // protobuf-java loads concrete Message classes via reflection at
        // descriptor-resolution time; tree-shaking them from a static call
        // graph misses the reflection edge and would NPE at runtime.
        // Keep all of protobuf-java and protobuf-kotlin whole; the rest
        // (KotlinPoet, kotlin-stdlib, kotlin-reflect) only uses
        // statically-resolvable references and minimizes safely.
        exclude(dependency("com.google.protobuf:protobuf-java:.*"))
        exclude(dependency("com.google.protobuf:protobuf-kotlin:.*"))
    }
}

// -------------------------------------------------------------------
// Maven publication: native binary as classifier artifact
// -------------------------------------------------------------------
//
// Each per-platform CI job invokes:
//
//     ./gradlew :plugin:publishToMavenLocal \
//         -PnativeBinaryFile=plugin/build/native/nativeCompile/protoc-gen-grpc-kotlin \
//         -PnativeBinaryClassifier=linux-x86_64
//
// producing
//
//     io.github.kotpb:kotpb-grpc-codegen:VERSION:linux-x86_64@exe
//
// Same GAV across every platform, only the classifier varies. Consumers
// then use the protobuf-gradle-plugin's standard syntax:
//
//     protobuf {
//         plugins {
//             id("grpckt") {
//                 artifact = "io.github.kotpb:kotpb-grpc-codegen:VERSION"
//             }
//         }
//     }
//
// and protobuf-gradle-plugin auto-resolves the matching classifier for
// the host OS and architecture.

publishing {
    publications {
        create<MavenPublication>("nativeBinary") {
            groupId = "io.github.kotpb"
            // Maven artifact-id is brand-prefixed and distinct from upstream's
            // io.grpc:protoc-gen-grpc-kotlin so neither artifact is mistaken
            // for the other. The native binary is still named
            // `protoc-gen-grpc-kotlin` so consumers' --grpc-kotlin_out=
            // invocations work unchanged.
            artifactId = "kotpb-grpc-codegen"
            version = project.version.toString()

            // Two modes:
            //   single — each per-platform CI job builds and publishes its
            //     classifier (used by mavenLocal smoke tests; pass
            //     -PnativeBinaryFile=... -PnativeBinaryClassifier=...).
            //     Optionally pair with -PjvmJarFile=... to also include the
            //     classifier=jvm fallback in the same publication.
            //   aggregate — the publish-maven-central CI job downloads every
            //     classifier .exe + the jvm.jar and publishes them in ONE
            //     Gradle invocation so the gradle-nexus-publish-plugin batches
            //     them into a single Sonatype Central deployment that
            //     close+release can finalize atomically
            //     (pass -PnativeBinariesDir=...).
            val singleFile = project.findProperty("nativeBinaryFile") as? String
            val singleClassifier = project.findProperty("nativeBinaryClassifier") as? String
            val singleJvmJar = project.findProperty("jvmJarFile") as? String
            val aggregateDir = project.findProperty("nativeBinariesDir") as? String
            check(
                ((singleFile == null) == (singleClassifier == null)) &&
                    !(aggregateDir != null && singleFile != null)
            ) {
                "Pass either (-PnativeBinaryFile + -PnativeBinaryClassifier) or " +
                    "-PnativeBinariesDir, not both."
            }
            if (singleFile != null && singleClassifier != null) {
                artifact(file(singleFile)) {
                    this.classifier = singleClassifier
                    extension = "exe" // Maven convention for protoc plugins, even on Unix.
                }
            }
            if (singleJvmJar != null) {
                artifact(file(singleJvmJar)) {
                    classifier = "jvm"
                    extension = "jar"
                }
            }
            if (aggregateDir != null) {
                val ver = project.version.toString()
                listOf("linux-x86_64", "linux-aarch_64", "osx-aarch_64", "windows-x86_64").forEach { cls ->
                    val f = file("$aggregateDir/kotpb-grpc-codegen-$ver-$cls.exe")
                    check(f.exists()) { "expected aggregated binary at $f (was the matrix complete?)" }
                    artifact(f) {
                        this.classifier = cls
                        extension = "exe"
                    }
                }
                // JVM fallback — built only on linux-x86_64 (the JAR's
                // contents are platform-independent).
                val jvmJar = file("$aggregateDir/kotpb-grpc-codegen-$ver-jvm.jar")
                check(jvmJar.exists()) { "expected JVM fallback jar at $jvmJar (was shadowJar uploaded?)" }
                artifact(jvmJar) {
                    classifier = "jvm"
                    extension = "jar"
                }
            }

            pom {
                name.set("kotpb-grpc-codegen")
                description.set(
                    "Pure-Kotlin protoc plugin for gRPC Kotlin coroutine stubs. " +
                        "Ships per-platform native binaries (no JVM required) " +
                        "and a classifier=jvm fat-JAR fallback for unsupported platforms."
                )
                url.set("https://github.com/Kotpb/kotpb-grpc-codegen")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("kotpb")
                        name.set("Kotpb maintainers")
                    }
                }
                scm {
                    url.set("https://github.com/Kotpb/kotpb-grpc-codegen")
                    connection.set("scm:git:https://github.com/Kotpb/kotpb-grpc-codegen.git")
                    developerConnection.set("scm:git:ssh://github.com/Kotpb/kotpb-grpc-codegen.git")
                }
            }
        }
    }

    repositories {
        // Useful for local consumer testing.
        mavenLocal()

        // gradle-nexus-publish-plugin (applied at root) wires the
        // `sonatype` repository pointed at central.sonatype.com when
        // SONATYPE_USERNAME / SONATYPE_PASSWORD env vars are set. The
        // CI publish-maven-central job uses
        //   ./gradlew :plugin:publishNativeBinaryPublicationToSonatypeRepository
        //   ./gradlew closeAndReleaseSonatypeStagingRepository
        // to do the Central Portal handoff.
    }
}

// Sign the published artifacts with our GPG key. Skipped silently when
// SIGNING_KEY isn't in the environment so local `publishToMavenLocal`
// invocations don't require GPG configured. CI sets both env vars from
// repo secrets. `isNullOrBlank` (not just `!= null`) because an empty
// secret is still a non-null env var, and useInMemoryPgpKeys(key, "")
// then fails inside the signing task with the unhelpful
// "Cannot query the value of this provider because it has no value available".
signing {
    val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
    val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["nativeBinary"])
    }
}
