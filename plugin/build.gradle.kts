plugins {
    id("grpckotlin.kotlin-conventions")
    application
    alias(libs.plugins.graalvm.native)
}

application {
    mainClass.set("io.github.grpckotlin.plugin.MainKt")
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
        mainClass.set("io.github.grpckotlin.plugin.MainKt")
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
            "--link-at-build-time=io.github.grpckotlin",

            // Speed-of-execution tier; native-image build itself takes longer
            // but the plugin runs once per protoc invocation so the runtime
            // win compounds across builds.
            "-O3",

            // Explicit "broad CPU compatibility" — never `native`, since the
            // produced binary is shipped to other machines.
            "-march=compatibility",

            // Cap runtime heap at 128 MiB. The default is 80% of physical RAM
            // — wasteful for a CLI invoked once per protoc build. Plenty of
            // headroom for KotlinPoet's intermediate buffers.
            "-R:MaxHeapSize=128m",

            // Better diagnostics if anything reflection-shaped breaks.
            "-H:+ReportExceptionStackTraces",
        )

        // Per-platform extras passed in by CI (e.g. `--static --libc=musl`
        // for the Linux runner so the binary is portable across glibc
        // versions and runnable inside alpine / distroless containers).
        val extraArgs = (project.findProperty("extraNativeBuildArgs") as? String)
            ?.split(' ')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (extraArgs.isNotEmpty()) buildArgs.addAll(extraArgs)

        // Use a GraalVM JDK 21 launcher specifically for native-image,
        // independent of the project's regular toolchain.
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
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
