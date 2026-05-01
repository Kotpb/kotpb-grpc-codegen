import com.google.protobuf.gradle.id

plugins {
    id("grpckotlin.kotlin-conventions")
    alias(libs.plugins.protobuf)
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

// Resolve the protoc artifact coordinates from the version catalog. We use the
// library accessor (libs.protobuf.protoc) rather than libs.versions.protobuf,
// because the type-safe accessor for a [versions] entry doesn't surface .get()
// inside the protobuf-gradle-plugin's configuration block on this Gradle version.
protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    plugins {
        id("grpckt") {
            path = grpcKotlinPluginPath
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpckt") {
                    option("comments=true")
                }
            }
            task.dependsOn(":plugin:installDist")
        }
    }
}
