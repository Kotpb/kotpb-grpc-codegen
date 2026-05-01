rootProject.name = "grpc-kotlin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    ":generator",
    ":plugin",
    ":generator-tests",
    ":e2e-tests",
)
