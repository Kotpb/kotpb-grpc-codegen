plugins {
    id("grpckotlin.kotlin-conventions")
}

dependencies {
    testImplementation(projects.generator)
    testImplementation(libs.kotlinpoet)
    testImplementation(libs.protobuf.kotlin)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj)
}
