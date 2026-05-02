import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.gitlab.arturbosch.detekt")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // JUnit 6 raised its baseline to Java 17, so the test deps demand 17
        // even though the generator + plugin would otherwise be happy on 11.
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(false)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// Reproducible JARs: stable file ordering and zero timestamps so identical
// inputs produce identical archive bytes.
tasks.withType<Jar>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

// Detekt: stock ruleset (no custom config), excluding KotlinPoet/protobuf-gradle
// generated output that lives under build/.
tasks.withType<Detekt>().configureEach {
    exclude("**/build/**")
}
