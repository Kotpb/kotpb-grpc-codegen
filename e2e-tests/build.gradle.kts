import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.protobuf.kotlin)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.api)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj)
    testImplementation(libs.grpc.inprocess)
    testImplementation(libs.coroutines.test)
}

val isWindows = System.getProperty("os.name").lowercase().contains("windows")
val grpcKotlinPluginExe = if (isWindows) "protoc-gen-grpc-kotlin.bat" else "protoc-gen-grpc-kotlin"
val grpcKotlinPluginPath: String =
    rootProject.layout.projectDirectory
        .dir("plugin/build/install/protoc-gen-grpc-kotlin/bin")
        .file(grpcKotlinPluginExe)
        .asFile
        .absolutePath

val protobufArtifactVersion: String = "4.35.0-RC1"

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufArtifactVersion"
    }
    plugins {
        id("grpckt") {
            path = grpcKotlinPluginPath
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpckt") { }
            }
            task.dependsOn(":plugin:installDist")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
