package io.github.kotpb.generator.tests

import io.github.kotpb.generator.GeneratorConfig
import io.github.kotpb.generator.GeneratorRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GeneratorOptionsTest {
    private fun runWithParameter(parameter: String): String {
        val request = TestFixtures.simpleRequestProto3().toBuilder()
            .setParameter(parameter)
            .build()
        return GeneratorRunner.run(request).getFile(0).content
    }

    private fun clearedFileOptions(
        clearJavaPackage: Boolean = false,
        clearJavaOuterClassname: Boolean = false,
        clearJavaMultipleFiles: Boolean = false,
    ) = TestFixtures.simpleRequestProto3().getProtoFile(0).options.toBuilder().also {
        if (clearJavaPackage) it.clearJavaPackage()
        if (clearJavaOuterClassname) it.clearJavaOuterClassname()
        if (clearJavaMultipleFiles) it.clearJavaMultipleFiles()
    }.build()

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
    fun `lite output omits schema descriptors and supplier classes`() {
        val content = runWithParameter("lite=true")
        assertThat(content).doesNotContain(".setSchemaDescriptor(")
        assertThat(content).doesNotContain("FileDescriptorSupplier")
        assertThat(content).doesNotContain("MethodDescriptorSupplier")
        assertThat(content).doesNotContain("ProtoServiceDescriptorSupplier")
        assertThat(content).doesNotContain("ProtoMethodDescriptorSupplier")
        assertThat(content).doesNotContain("findServiceByName")
        assertThat(content).doesNotContain("Descriptors")
    }

    @Test
    fun `default mode keeps schema descriptors and supplier classes`() {
        val content = runWithParameter("")
        assertThat(content).contains(".setSchemaDescriptor(")
        assertThat(content).contains("FileDescriptorSupplier")
        assertThat(content).contains("MethodDescriptorSupplier")
    }

    @Test
    fun `java_package option overrides the file's java_package`() {
        val content = runWithParameter("java_package=io.alternate.echo")
        assertThat(content).contains("package io.alternate.echo")
    }

    @Test
    fun `falls back to proto package when java_package is absent`() {
        val req = TestFixtures.rewriteFile(TestFixtures.simpleRequestProto3()) {
            setOptions(clearedFileOptions(clearJavaPackage = true))
        }

        val response = GeneratorRunner.run(req)
        assertThat(response.getFile(0).name).startsWith("test/echo/")
        assertThat(response.getFile(0).content).contains("package test.echo")
    }

    @Test
    fun `derives outer class name from filename when java_outer_classname is absent`() {
        val req = TestFixtures.rewriteFile(TestFixtures.simpleRequestProto3()) {
            setOptions(
                clearedFileOptions(
                    clearJavaOuterClassname = true,
                    clearJavaMultipleFiles = true,
                )
            )
        }

        val response = GeneratorRunner.run(req)
        assertThat(response.getFile(0).name).isEqualTo("com/example/echo/EchoGrpcKt.kt")
    }

    @Test
    fun `derives PascalCase outer class from snake_case filename`() {
        val req = TestFixtures.rewriteFile(
            TestFixtures.simpleRequestProto3(),
            withFileName = "test/echo/my_great_proto.proto",
        ) {
            setOptions(
                clearedFileOptions(
                    clearJavaOuterClassname = true,
                    clearJavaMultipleFiles = true,
                )
            )
        }

        val response = GeneratorRunner.run(req)
        assertThat(response.getFile(0).name).isEqualTo("com/example/echo/MyGreatProtoGrpcKt.kt")
    }

    @Test
    fun `appends OuterClass suffix when derived name matches a message`() {
        val req = TestFixtures.rewriteFile(
            TestFixtures.simpleRequestProto3(),
            withFileName = "test/echo/echo_request.proto",
        ) {
            setOptions(
                clearedFileOptions(
                    clearJavaOuterClassname = true,
                    clearJavaMultipleFiles = true,
                )
            )
        }

        val response = GeneratorRunner.run(req)
        assertThat(response.getFile(0).name)
            .isEqualTo("com/example/echo/EchoRequestOuterClassGrpcKt.kt")
    }

    @Test
    fun `appends OuterClass suffix when derived name matches the service`() {
        val req = TestFixtures.rewriteFile(
            TestFixtures.simpleRequestProto3(),
            withFileName = "test/echo/echo_service.proto",
        ) {
            setOptions(
                clearedFileOptions(
                    clearJavaOuterClassname = true,
                    clearJavaMultipleFiles = true,
                )
            )
        }

        val response = GeneratorRunner.run(req)
        assertThat(response.getFile(0).name)
            .isEqualTo("com/example/echo/EchoServiceOuterClassGrpcKt.kt")
    }

    @Test
    fun `bundled mode references derived outer class in supplier and message types`() {
        val req = TestFixtures.rewriteFile(TestFixtures.simpleRequestProto3()) {
            setOptions(
                clearedFileOptions(
                    clearJavaOuterClassname = true,
                    clearJavaMultipleFiles = true,
                )
            )
        }

        val content = GeneratorRunner.run(req).getFile(0).content
        assertThat(content).contains("Echo.getDescriptor()")
        assertThat(content).contains("Echo.EchoRequest.getDefaultInstance()")
        assertThat(content).contains("Echo.EchoResponse.getDefaultInstance()")
    }

    @Test
    fun `generated file has a generated header comment`() {
        val content = runWithParameter("")
        assertThat(content).contains("DO NOT EDIT")
    }

    @Test
    fun `abstract_server_methods makes every server method abstract, without a default body`() {
        val content = runWithParameter("abstract_server_methods")
        assertThat(content).contains("public abstract suspend fun unary(request: EchoRequest): EchoResponse\n")
        assertThat(content).contains("public abstract fun serverStream(request: EchoRequest): Flow<EchoResponse>\n")
        assertThat(content).contains(
            "public abstract suspend fun clientStream(requests: Flow<EchoRequest>): EchoResponse\n"
        )
        assertThat(content).contains(
            "public abstract fun bidiStream(requests: Flow<EchoRequest>): Flow<EchoResponse>\n"
        )
        assertThat(content).doesNotContain("public open ")
        assertThat(content).doesNotContain("= throw StatusException")
    }

    @Test
    fun `abstract_server_methods preserves NotImplementedError handling in bindService`() {
        val content = runWithParameter("abstract_server_methods")
        assertThat(content).contains("catch (e: NotImplementedError)")
    }

    @Test
    fun `abstract_server_methods=false keeps server methods open`() {
        val content = runWithParameter("abstract_server_methods=false")
        assertThat(content).contains("public open suspend fun unary(")
        assertThat(content).doesNotContain("public abstract suspend fun")
    }

    @Test
    fun `abstract_server_methods combines with other options`() {
        val content = runWithParameter("lite,abstract_server_methods")
        assertThat(content).contains("ProtoLiteUtils.marshaller(")
        assertThat(content).contains("public abstract suspend fun unary(")
    }

    @Test
    fun `unknown plugin option throws IllegalArgumentException`() {
        val error = assertThrows<IllegalArgumentException> {
            GeneratorConfig.parse("abstract_server_method")
        }
        assertThat(error).hasMessageContaining("abstract_server_method")
    }

    @Test
    fun `invalid boolean option value throws IllegalArgumentException`() {
        val error = assertThrows<IllegalArgumentException> {
            GeneratorConfig.parse("abstract_server_methods=yes")
        }
        assertThat(error).hasMessageContaining("abstract_server_methods")
        assertThat(error).hasMessageContaining("yes")

        assertThrows<IllegalArgumentException> {
            GeneratorConfig.parse("lite=")
        }
    }

    @Test
    fun `java_package without a non-empty value throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            GeneratorConfig.parse("java_package")
        }
        assertThrows<IllegalArgumentException> {
            GeneratorConfig.parse("java_package=")
        }
    }
}
