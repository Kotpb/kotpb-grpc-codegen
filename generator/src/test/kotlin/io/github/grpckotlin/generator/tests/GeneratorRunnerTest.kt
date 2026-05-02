package io.github.grpckotlin.generator.tests

import com.google.protobuf.DescriptorProtos.Edition
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import io.github.grpckotlin.generator.GeneratorRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratorRunnerTest {
    @Test
    fun `declares editions support and proto3-optional support`() {
        val response = GeneratorRunner.run(TestFixtures.simpleRequestProto3())

        val proto3Optional = CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL_VALUE.toLong()
        val supportsEditions = CodeGeneratorResponse.Feature.FEATURE_SUPPORTS_EDITIONS_VALUE.toLong()
        assertThat(response.supportedFeatures and proto3Optional).isEqualTo(proto3Optional)
        assertThat(response.supportedFeatures and supportsEditions).isEqualTo(supportsEditions)
    }

    @Test
    fun `declares minimum and maximum editions`() {
        val response = GeneratorRunner.run(TestFixtures.simpleRequestProto3())

        assertThat(response.minimumEdition).isEqualTo(Edition.EDITION_PROTO2.number)
        assertThat(response.maximumEdition).isEqualTo(Edition.EDITION_2024.number)
    }

    @Test
    fun `multi-files mode emits one file per service named after the service`() {
        val response = GeneratorRunner.run(TestFixtures.simpleRequestProto3())

        assertThat(response.fileList).hasSize(1)
        assertThat(response.getFile(0).name).isEqualTo("com/example/echo/EchoServiceGrpcKt.kt")
    }

    @Test
    fun `bundled mode emits a single file named after the proto's outer class`() {
        val response = GeneratorRunner.run(TestFixtures.bundledRequestProto3())

        assertThat(response.fileList).hasSize(1)
        assertThat(response.getFile(0).name).isEqualTo("com/example/echo/EchoProtoGrpcKt.kt")
    }

    @Test
    fun `emits no file for proto without services`() {
        val request = TestFixtures.simpleRequestProto3().toBuilder().apply {
            val protoNoSvc = getProtoFile(0).toBuilder().clearService().build()
            clearProtoFile()
            addProtoFile(protoNoSvc)
        }.build()

        val response = GeneratorRunner.run(request)

        assertThat(response.fileList).isEmpty()
    }
}
