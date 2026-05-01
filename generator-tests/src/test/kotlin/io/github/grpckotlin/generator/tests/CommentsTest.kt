package io.github.grpckotlin.generator.tests

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import io.github.grpckotlin.generator.GeneratorRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommentsTest {
    private fun run(request: CodeGeneratorRequest, parameter: String): String {
        val withParam = request.toBuilder().setParameter(parameter).build()
        return GeneratorRunner.run(withParam).getFile(0).content
    }

    @Test
    fun `default config does not emit proto comments`() {
        val content = run(TestFixtures.requestWithComments(), parameter = "")
        assertThat(content).doesNotContain("EchoService is a simple echo service")
        assertThat(content).doesNotContain("Sends an echo request")
    }

    @Test
    fun `comments=true attaches service comment to stub and impl base`() {
        val content = run(TestFixtures.requestWithComments(), parameter = "comments=true")
        // KotlinPoet emits KDoc as `/** */` immediately above the declaration.
        // The service comment appears on the outer object, the stub class, and
        // the impl base class — three KDoc blocks total for the service text.
        val matches = Regex("""/\*\*[\s*]*EchoService is a simple echo service""")
            .findAll(content).count()
        assertThat(matches).isGreaterThanOrEqualTo(3)
    }

    @Test
    fun `comments=true attaches method comment to all three method declarations`() {
        val content = run(TestFixtures.requestWithComments(), parameter = "comments=true")
        // Each method's comment appears 3x: on the MethodDescriptor property,
        // on the client stub fun, and on the server impl-base fun.
        val unaryMatches = Regex("""Sends an echo request and waits for the reply""")
            .findAll(content).count()
        assertThat(unaryMatches).isEqualTo(3)
    }

    @Test
    fun `comments=true preserves multi-line comments`() {
        val content = run(TestFixtures.requestWithComments(), parameter = "comments=true")
        assertThat(content).contains("EchoService is a simple echo service.")
        assertThat(content).contains("It echoes back what it receives.")
    }

    @Test
    fun `comments shorthand without value enables the flag`() {
        val content = run(TestFixtures.requestWithComments(), parameter = "comments")
        assertThat(content).contains("EchoService is a simple echo service")
    }

    @Test
    fun `comments=false explicitly disables the flag`() {
        val content = run(TestFixtures.requestWithComments(), parameter = "comments=false")
        assertThat(content).doesNotContain("EchoService is a simple echo service")
    }

    @Test
    fun `comments flag composes with other flags`() {
        val content = run(
            TestFixtures.requestWithComments(),
            parameter = "lite=true,comments=true",
        )
        assertThat(content).contains("ProtoLiteUtils.marshaller(")
        assertThat(content).contains("EchoService is a simple echo service")
    }

    @Test
    fun `comments=true is a no-op when the file has no SourceCodeInfo`() {
        val content = run(TestFixtures.simpleRequestProto3(), parameter = "comments=true")
        // No leading_comments anywhere -> no KDoc blocks emitted. The file-level
        // generated-by header uses // style, not /**, so we can check directly.
        assertThat(content).doesNotContain("/**")
    }
}
