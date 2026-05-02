package io.github.grpckotlin.e2e

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Verifies that proto-source comments survive the full pipeline:
 * `.proto` -> protoc -> our plugin -> generated `.kt` on disk.
 *
 * The e2e build configures the grpckt plugin with `comments=true`, and
 * `proto3_multifiles.proto` (the comments-bearing fixture) has leading
 * comments on the service and on every RPC.
 */
class GeneratedCommentsTest {
    private val generatedSource: String by lazy {
        // proto3_multifiles.proto has java_multiple_files=true, so we emit
        // one .kt per service named after the service rather than the
        // proto's outer class.
        val path = Paths.get(
            "build", "generated", "sources", "proto", "main",
            "grpckt", "com", "example", "proto3_multifiles", "EchoServiceGrpcKt.kt",
        )
        check(Files.exists(path)) { "Generated source not found at $path" }
        Files.readString(path)
    }

    @Test
    fun `service-level comment appears in generated source`() {
        assertThat(generatedSource).contains("EchoService is the canonical fixture")
    }

    @Test
    fun `unary method comment appears in generated source`() {
        assertThat(generatedSource).contains("Unary echo: sends one request and waits for one reply")
    }

    @Test
    fun `server streaming method comment appears`() {
        assertThat(generatedSource).contains("Server streaming: one request, many replies")
    }

    @Test
    fun `client streaming method comment appears`() {
        assertThat(generatedSource).contains("Client streaming: many requests, one reply")
    }

    @Test
    fun `bidi method comment appears`() {
        assertThat(generatedSource).contains("Bidirectional streaming: many requests, many replies")
    }

    @Test
    fun `comments are emitted as KDoc blocks`() {
        // KotlinPoet renders KDoc as /** ... */ blocks. With our config every
        // service/method comment appears multiple times (outer object + stub +
        // impl base for the service; descriptor + stub fn + impl fn for each
        // method) so we expect many KDoc blocks in this file.
        val kdocCount = Regex("""/\*\*""").findAll(generatedSource).count()
        assertThat(kdocCount).isGreaterThanOrEqualTo(15) // 3 service + 3*4 methods
    }
}
