package io.github.grpckotlin.generator.tests

import io.github.grpckotlin.generator.GeneratorRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NoGrpcJavaReferenceTest {
    @Test
    fun `generated content does not reference grpc-java service classes`() {
        val response = GeneratorRunner.run(TestFixtures.simpleRequestProto3())
        val content = response.getFile(0).content

        // The grpc-java compiler emits a single class named "<Service>Grpc" containing
        // ImplBase, BlockingStub, FutureStub and per-method descriptor accessors. Our
        // self-contained output must not depend on any of those — so any reference to
        // "<ServiceName>Grpc" or its known nested members is a regression.
        val forbiddenPatterns = listOf(
            Regex("""\bEchoServiceGrpc\b"""),
            Regex("""EchoServiceImplBase"""),
            Regex("""EchoServiceBlockingStub"""),
            Regex("""EchoServiceFutureStub"""),
        )
        for (pattern in forbiddenPatterns) {
            assertThat(pattern.containsMatchIn(content))
                .withFailMessage(
                    "Generated content contains forbidden grpc-java reference: %s\n--- generated ---\n%s",
                    pattern.pattern, content,
                )
                .isFalse()
        }
    }
}
