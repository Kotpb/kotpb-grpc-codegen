package io.github.kotpb.generator.tests

import io.github.kotpb.generator.GeneratorRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeprecationTest {
    private val deprecatedContent: String by lazy {
        GeneratorRunner.run(TestFixtures.requestWithDeprecation()).getFile(0).content
    }
    private val deprecatedAbstractContent: String by lazy {
        val request = TestFixtures.requestWithDeprecation().toBuilder().setParameter("abstract_server_methods").build()
        GeneratorRunner.run(request).getFile(0).content
    }
    private val plainContent: String by lazy {
        GeneratorRunner.run(TestFixtures.simpleRequestProto3()).getFile(0).content
    }

    @Test
    fun `non-deprecated proto produces no Deprecated annotations`() {
        assertThat(plainContent).doesNotContain("@Deprecated")
    }

    @Test
    fun `deprecated service marks the CoroutineStub class`() {
        assertThat(deprecatedContent).containsPattern(
            """@Deprecated\("This service is deprecated\."\)\s+public class EchoServiceCoroutineStub"""
        )
    }

    @Test
    fun `deprecated service marks the CoroutineImplBase class`() {
        assertThat(deprecatedContent).containsPattern(
            """@Deprecated\("This service is deprecated\."\)\s+public abstract class EchoServiceCoroutineImplBase"""
        )
    }

    @Test
    fun `with abstract_server_methods, a deprecated service still marks CoroutineImplBase`() {
        assertThat(deprecatedAbstractContent).containsPattern(
            """@Deprecated\("This service is deprecated\."\)\s+public abstract class EchoServiceCoroutineImplBase"""
        )
    }

    @Test
    fun `deprecated method marks the client stub function`() {
        assertThat(deprecatedContent).containsPattern(
            """@Deprecated\("This RPC is deprecated\."\)\s+public suspend fun unary\("""
        )
    }

    @Test
    fun `deprecated method marks the server impl-base function`() {
        assertThat(deprecatedContent).containsPattern(
            """@Deprecated\("This RPC is deprecated\."\)\s+public open suspend fun unary\("""
        )
    }

    @Test
    fun `with abstract_server_methods, a deprecated method is abstract like the others`() {
        assertThat(deprecatedAbstractContent).containsPattern(
            """@Deprecated\("This RPC is deprecated\."\)\s+public abstract suspend fun unary\("""
        )
    }

    @Test
    fun `deprecated method marks the MethodDescriptor property`() {
        assertThat(deprecatedContent).containsPattern(
            """@Deprecated\("This RPC is deprecated\."\)\s+public val getUnaryMethod"""
        )
    }

    @Test
    fun `non-deprecated methods alongside a deprecated one stay unmarked`() {
        val pattern = Regex(
            """@Deprecated\("This RPC is deprecated\."\)\s+public\s+""" +
                """(?:(?:open|abstract)\s+)?(?:suspend\s+)?(?:val|fun)\s+(\w+)"""
        )
        val annotatedDeclarations = pattern.findAll(deprecatedContent).map { it.groupValues[1] }.toList()
        assertThat(annotatedDeclarations).containsExactlyInAnyOrder("getUnaryMethod", "unary", "unary")
    }

    @Test
    fun `bindService gets DEPRECATION suppression when methods are deprecated`() {
        assertThat(deprecatedContent).containsPattern(
            """@Suppress\("DEPRECATION"\)\s+final override fun bindService\("""
        )
    }
}
