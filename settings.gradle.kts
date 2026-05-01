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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":generator",
    ":plugin",
    ":generator-tests",
    ":e2e-tests",
)
