import com.google.protobuf.gradle.id

plugins {
    id("grpckotlin.kotlin-conventions")
    alias(libs.plugins.protobuf)
}

dependencies {
    implementation(platform(libs.grpc.bom))
    implementation(libs.protobuf.kotlin)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.api)
    implementation(libs.coroutines.core)

    testImplementation(platform(libs.junit.bom))
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

// libs.versions.protobuf.get() doesn't resolve inside the protobuf-gradle-plugin's
// configuration block (Kotlin DSL accessor quirk, persists into Gradle 9.5);
// the library accessor for the `protoc` artifact does, and toString() on a
// MinimalExternalModuleDependency returns "group:name:version" — exactly what
// `artifact` wants.
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
