package io.github.grpckotlin.generator.tests

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import io.github.grpckotlin.generator.GeneratorRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratorOptionsTest {
    private fun runWithParameter(parameter: String): String {
        val request = TestFixtures.simpleRequestProto3().toBuilder()
            .setParameter(parameter)
            .build()
        val response = GeneratorRunner.run(request)
        return response.getFile(0).content
    }

    @Test
    fun `default options use ProtoUtils marshaller`() {
        val content = runWithParameter("")
        assertThat(content).contains("ProtoUtils.marshaller(")
        assertThat(content).doesNotContain("ProtoLiteUtils.marshaller(")
    }

    @Test
    fun `lite option switches to ProtoLiteUtils marshaller`() {
        val content = runWithParameter("lite=true")
        assertThat(content).contains("ProtoLiteUtils.marshaller(")
        assertThat(content).doesNotContain("ProtoUtils.marshaller(")
    }

    @Test
    fun `lite option without value defaults to true`() {
        val content = runWithParameter("lite")
        assertThat(content).contains("ProtoLiteUtils.marshaller(")
    }

    @Test
    fun `java_package option overrides the file's java_package`() {
        val content = runWithParameter("java_package=io.alternate.echo")
        assertThat(content).contains("package io.alternate.echo")
    }

    @Test
    fun `generated file has a generated header comment`() {
        val content = runWithParameter("")
        assertThat(content).contains("DO NOT EDIT")
    }
}
