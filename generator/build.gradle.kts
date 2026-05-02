plugins {
    id("grpckotlin.kotlin-conventions")
}

dependencies {
    implementation(libs.kotlinpoet)
    implementation(libs.protobuf.kotlin)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj)
}
