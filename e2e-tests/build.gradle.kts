import com.google.protobuf.gradle.id

plugins {
    id("kotpb.kotlin-conventions")
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

// A fixture generated with --grpc-kotlin_opt=abstract_server_methods. protoc's
// plugin options apply to a whole generate task, so it has a source set, and
// a task, of its own, which the tests compile and run against.
val abstractServerMethods: SourceSet = sourceSets.create("abstractServerMethods")
configurations.named(abstractServerMethods.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}
sourceSets.test {
    compileClasspath += abstractServerMethods.output
    runtimeClasspath += abstractServerMethods.output
}

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
                    if (task.sourceSet.name == abstractServerMethods.name) option("abstract_server_methods")
                }
            }
            task.dependsOn(":plugin:installDist")
        }
    }
}
