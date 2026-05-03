import io.github.kotpb.buildsrc.DownloadHyperfineTask
import org.gradle.internal.os.OperatingSystem

plugins {
    id("kotpb.kotlin-conventions")
}

// ---- Cross-platform naming -------------------------------------------------

val isWindows: Boolean = OperatingSystem.current().isWindows

fun exeName(base: String): String = if (isWindows) "$base.exe" else base
fun scriptName(base: String): String = if (isWindows) "$base.bat" else base

// Maven Central convention used by both protoc and protoc-gen-grpc-java
// (matches what .github/workflows/native-binaries.yml uses for our own
// artifacts).
val protocClassifier: String = run {
    val os = OperatingSystem.current()
    val arch = System.getProperty("os.arch").lowercase()
    val osPart = when {
        os.isLinux -> "linux"
        os.isMacOsX -> "osx"
        os.isWindows -> "windows"
        else -> error("Unsupported OS: $os")
    }
    val archPart = when (arch) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "aarch_64"
        else -> error("Unsupported arch: $arch")
    }
    "$osPart-$archPart"
}

// Hyperfine release asset naming (rust-style triples, not Maven-style).
val hyperfineAsset: String = run {
    val os = OperatingSystem.current()
    val arch = System.getProperty("os.arch").lowercase()
    when {
        os.isWindows && arch in listOf("amd64", "x86_64") -> "x86_64-pc-windows-msvc.zip"
        os.isLinux && arch in listOf("amd64", "x86_64") -> "x86_64-unknown-linux-musl.tar.gz"
        os.isLinux && arch in listOf("aarch64", "arm64") -> "aarch64-unknown-linux-gnu.tar.gz"
        os.isMacOsX && arch in listOf("amd64", "x86_64") -> "x86_64-apple-darwin.tar.gz"
        os.isMacOsX && arch in listOf("aarch64", "arm64") -> "aarch64-apple-darwin.tar.gz"
        else -> error("No hyperfine asset for $os/$arch")
    }
}

val hyperfineExe = exeName("hyperfine")
val protocExe = exeName("protoc")
val grpcJavaExe = exeName("protoc-gen-grpc-java")
val upstreamKotlinScript = scriptName("protoc-gen-grpc-kotlin")

// ---- Tooling artifacts pulled from Maven Central ---------------------------

val benchTools: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    // Versions with sibling entries (`grpc-kotlin`, `protobuf-plugin`) need
    // `.asProvider()` to disambiguate the leaf from the namespace.
    val grpcVer = libs.versions.grpc.asProvider().get()
    val grpcKtVer = libs.versions.grpc.kotlin.get()
    val protobufVer = libs.versions.protobuf.asProvider().get()

    benchTools("com.google.protobuf:protoc:$protobufVer:$protocClassifier@exe")
    benchTools("io.grpc:protoc-gen-grpc-java:$grpcVer:$protocClassifier@exe")
    benchTools("io.grpc:protoc-gen-grpc-kotlin:$grpcKtVer:jdk8@jar")
}

// ---- Runtime knobs ---------------------------------------------------------

val variantsFilter: String? = project.findProperty("variants")?.toString()
val fixturesFilter: String? = project.findProperty("fixtures")?.toString()

// When OURS_NATIVE is excluded we skip depending on :plugin:nativeCompile so
// users without GraalVM can still run the bench.
val needsNative: Boolean = variantsFilter?.let { "OURS_NATIVE" in it } ?: true

// ---- Layout ----------------------------------------------------------------

val benchToolsDir = layout.buildDirectory.dir("bench-tools")
val benchGenDir = layout.buildDirectory.dir("generated/bench")

// ---- Tasks: build inputs (config-cache friendly, properly UTD-checked) -----

// Resolves the three Maven Central tools into bench-tools/, with
// platform-correct names. Standard `Copy` does the heavy lifting.
val installBenchBinaries by tasks.registering(Copy::class) {
    val protocLocal = protocExe
    val grpcJavaLocal = grpcJavaExe

    from(benchTools)
    into(benchToolsDir)
    rename { name ->
        when {
            // Order matters: `protoc-gen-grpc-*` must match before bare `protoc-`.
            name.startsWith("protoc-gen-grpc-java") -> grpcJavaLocal
            name.startsWith("protoc-gen-grpc-kotlin") -> "protoc-gen-grpc-kotlin.jar"
            name.startsWith("protoc-") -> protocLocal
            else -> name
        }
    }
    if (!isWindows) filePermissions { unix("0755") }
}

// The upstream Kotlin generator ships as a fat jar; protoc invokes plugins as
// executables, so wrap the jar in a per-OS shell script.
val writeBenchWrapper by tasks.registering {
    val isWin = isWindows
    val targetFile = benchToolsDir.map { it.file(upstreamKotlinScript) }
    val content = if (isWin) {
        "@echo off\r\njava -jar \"%~dp0protoc-gen-grpc-kotlin.jar\" %*\r\n"
    } else {
        "#!/bin/sh\nexec java -jar \"\$(dirname \"\$0\")/protoc-gen-grpc-kotlin.jar\" \"\$@\"\n"
    }

    inputs.property("isWindows", isWin)
    inputs.property("content", content)
    outputs.file(targetFile)

    doLast {
        val target = targetFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(content)
        if (!isWin) target.setExecutable(true, false)
    }
}

val downloadHyperfine by tasks.registering(DownloadHyperfineTask::class) {
    version.set(libs.versions.hyperfine.get())
    asset.set(hyperfineAsset)
    destExe.set(benchToolsDir.map { it.file(hyperfineExe) })
    workDir.set(layout.buildDirectory.dir("bench-tools/_work"))
}

val writeBenchProperties by tasks.registering(WriteProperties::class) {
    destinationFile.set(benchGenDir.map { it.file("bench.properties") })

    val rootDir = rootProject.layout.projectDirectory
    val pluginInstallScript = rootDir.file(
        "plugin/build/install/protoc-gen-grpc-kotlin/bin/${scriptName("protoc-gen-grpc-kotlin")}"
    )
    val pluginNativeBinary = rootDir.file(
        "plugin/build/native/nativeCompile/${exeName("protoc-gen-grpc-kotlin")}"
    )

    property("protoc", benchToolsDir.map { it.file(protocExe).asFile.absolutePath })
    property("ours.jvm", pluginInstallScript.asFile.absolutePath)
    property("ours.native", pluginNativeBinary.asFile.absolutePath)
    property("upstream.java", benchToolsDir.map { it.file(grpcJavaExe).asFile.absolutePath })
    property("upstream.kotlin", benchToolsDir.map { it.file(upstreamKotlinScript).asFile.absolutePath })
    property("hyperfine", benchToolsDir.map { it.file(hyperfineExe).asFile.absolutePath })
    property("fixtures.dir", projectDir.resolve("src/main/proto-bench").absolutePath)
    property("out.root", layout.buildDirectory.dir("bench-out").map { it.asFile.absolutePath })
    property("reports.dir", layout.buildDirectory.dir("reports/bench").map { it.asFile.absolutePath })

    // The properties file references our plugin's installDist + native binary
    // paths, so writing should not race ahead of the upstream tasks producing
    // them. The bench.properties values themselves don't change with content
    // (they're path strings derived from the build layout), but ensuring the
    // pointed-to artifacts exist on disk is what makes the bench actually work.
    dependsOn(":plugin:installDist")
    if (needsNative) dependsOn(":plugin:nativeCompile")
}

// Lifecycle task users invoke when they want to refresh all bench inputs in
// one go (e.g. before running `:benchmark:bench`). Internally it just chains
// the four real tasks.
val prepareBenchTools by tasks.registering {
    dependsOn(installBenchBinaries, writeBenchWrapper, downloadHyperfine, writeBenchProperties)
    description = "Resolves protoc + upstream plugins, downloads hyperfine, " +
        "writes bench.properties for the runtime to consume."
    group = "benchmark"
}

sourceSets.main { resources.srcDir(benchGenDir) }
tasks.named<ProcessResources>("processResources") { dependsOn(writeBenchProperties) }

// ---- Tasks the user runs ---------------------------------------------------

fun JavaExec.applyFilters() {
    variantsFilter?.let { systemProperty("variants", it) }
    fixturesFilter?.let { systemProperty("fixtures", it) }
}

tasks.register<JavaExec>("bench") {
    group = "benchmark"
    description = "Time each (variant × fixture) cell with hyperfine. " +
        "Writes results.json + results.md under build/reports/bench."
    dependsOn(prepareBenchTools)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.kotpb.benchmark.HyperfineRunner")
    applyFilters()
}

tasks.register<JavaExec>("regenFixtures") {
    group = "benchmark"
    description = "(Re)generate small/medium/large .proto fixtures. Run only when changing sizes."
    // Compiled classes only (not runtimeClasspath) so we don't pull
    // processResources → prepareBenchTools and avoid needing GraalVM/hyperfine.
    classpath = sourceSets.main.get().output.classesDirs
    mainClass.set("io.github.kotpb.benchmark.FixtureGenerator")
    args(projectDir.resolve("src/main/proto-bench").absolutePath)
}
