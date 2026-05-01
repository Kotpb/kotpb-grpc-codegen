plugins {
    id("grpckotlin.kotlin-conventions")
    application
}

application {
    mainClass.set("io.github.grpckotlin.plugin.MainKt")
    applicationName = "protoc-gen-grpc-kotlin"
}

dependencies {
    implementation(projects.generator)
}
