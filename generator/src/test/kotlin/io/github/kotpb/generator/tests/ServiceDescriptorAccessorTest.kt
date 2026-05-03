package io.github.kotpb.generator.tests

import io.github.kotpb.generator.GeneratorRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceDescriptorAccessorTest {
    private val content: String by lazy {
        GeneratorRunner.run(TestFixtures.simpleRequestProto3()).getFile(0).content
    }

    @Test
    fun `stub and impl-base each expose serviceDescriptor via a JvmStatic companion`() {
        // The exact accessor body should appear twice — once per nested class
        // (stub + impl base). Both delegate to the outer GrpcKt object so the
        // descriptor identity stays singular.
        val accessor = Regex(
            """@JvmStatic\s+public val serviceDescriptor: GrpcServiceDescriptor\s+""" +
                """get\(\) = EchoServiceGrpcKt\.serviceDescriptor"""
        )
        assertThat(accessor.findAll(content).count()).isEqualTo(2)
    }

    @Test
    fun `the accessors live inside companion objects, not as top-level members`() {
        // Two companion-object declarations expected: one in the stub, one in
        // the impl base. The outer GrpcKt object itself has no companion.
        val companionCount = Regex("""public companion object \{""")
            .findAll(content).count()
        assertThat(companionCount).isEqualTo(2)
    }
}
