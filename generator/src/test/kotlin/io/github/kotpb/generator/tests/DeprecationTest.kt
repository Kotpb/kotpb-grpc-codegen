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
    fun `deprecated method marks the MethodDescriptor property`() {
        assertThat(deprecatedContent).containsPattern(
            """@Deprecated\("This RPC is deprecated\."\)\s+public val getUnaryMethod"""
        )
    }

    @Test
    fun `non-deprecated methods alongside a deprecated one stay unmarked`() {
        // Only Unary is marked deprecated in the fixture. Each per-method
        // annotation lands on the descriptor property + stub fn + impl fn = 3.
        val deprecatedMethodMarkers = Regex("""@Deprecated\("This RPC is deprecated\."\)""")
            .findAll(deprecatedContent).count()
        assertThat(deprecatedMethodMarkers).isEqualTo(3)
    }

    @Test
    fun `bindService gets DEPRECATION suppression when methods are deprecated`() {
        assertThat(deprecatedContent).containsPattern(
            """@Suppress\("DEPRECATION"\)\s+final override fun bindService\("""
        )
    }
}
