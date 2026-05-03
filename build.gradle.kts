plugins {
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

allprojects {
    group = "io.github.kotpb"
    // version is sourced from gradle.properties (release-please rewrites
    // it on each release PR); leave it alone here.
}

// Sonatype Central is the modern Maven Central publisher for new namespaces
// like ours. Credentials come from env vars set by the publish-maven-central
// CI job — local `:plugin:publishToMavenLocal` invocations don't need them.
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://central.sonatype.com/api/v1/publisher/"))
            username.set(providers.environmentVariable("SONATYPE_USERNAME"))
            password.set(providers.environmentVariable("SONATYPE_PASSWORD"))
        }
    }
}

tasks.named<Wrapper>("wrapper") {
    distributionType = Wrapper.DistributionType.BIN
    validateDistributionUrl = true
}
