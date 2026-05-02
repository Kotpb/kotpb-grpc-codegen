package io.github.grpckotlin.e2e

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * End-to-end proof for grpc/grpc-kotlin#631-style behaviour: when a `.proto`
 * file has messages but no `service`, our plugin must emit nothing for it.
 * No crash, no zero-byte stub file, no `*GrpcKt.kt` of any kind.
 *
 * The fixture (`messages_only.proto`) is processed by protoc as part of the
 * normal e2e build, so the run that produces this test's binaries is also
 * the run that exercises the contract.
 */
class NoServiceNoOutputTest {
    private val grpcKtRoot = Paths.get("build", "generated", "sources", "proto", "main", "grpckt")
    private val javaRoot = Paths.get("build", "generated", "sources", "proto", "main", "java")

    @Test
    fun `protoc-gen-java still emits message classes for messages_only_proto`() {
        // Sanity check that the proto was actually picked up by the build:
        // the Java messages should exist. If this fails, the assertions below
        // would pass for the wrong reason.
        val ping = javaRoot.resolve("com/example/messages_only/Ping.java")
        val pong = javaRoot.resolve("com/example/messages_only/Pong.java")
        assertThat(Files.exists(ping)).withFailMessage("expected $ping").isTrue()
        assertThat(Files.exists(pong)).withFailMessage("expected $pong").isTrue()
    }

    @Test
    fun `our plugin emits no Kotlin file for messages_only_proto`() {
        val expectedIfWeGenerated = grpcKtRoot.resolve(
            "com/example/messages_only/MessagesOnlyProtoGrpcKt.kt"
        )
        assertThat(Files.exists(expectedIfWeGenerated))
            .withFailMessage(
                "Plugin should not emit a Kotlin file for a .proto with no services, " +
                    "but %s exists.", expectedIfWeGenerated,
            )
            .isFalse()
    }

    @Test
    fun `the messages_only directory is empty under grpckt output`() {
        // Catch-all: the entire directory tree under grpckt for this proto's
        // package should not exist (or be empty), even if a future change
        // started emitting a differently-named file.
        val pkgDir = grpcKtRoot.resolve("com/example/messages_only")
        if (Files.exists(pkgDir)) {
            val entries = Files.newDirectoryStream(pkgDir).use { it.toList() }
            assertThat(entries)
                .withFailMessage("expected no files under %s, found %s", pkgDir, entries)
                .isEmpty()
        }
    }
}
