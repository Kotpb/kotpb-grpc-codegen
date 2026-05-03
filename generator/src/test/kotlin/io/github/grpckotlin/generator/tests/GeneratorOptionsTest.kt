package io.github.grpckotlin.generator.tests

import io.github.grpckotlin.generator.GeneratorRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
        // The lite runtime (protobuf-javalite + grpc-protobuf-lite) doesn't
        // ship Descriptors.FileDescriptor or the io.grpc.protobuf.Proto*
        // DescriptorSupplier interfaces, so emitting them would not compile
        // for lite consumers. Upstream protoc-gen-grpc-java does the same
        // omission in lite mode.
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
        // Bundled mode (clear java_multiple_files) so the outer class name
        // appears in the generated file's name.
        val req = TestFixtures.rewriteFile(TestFixtures.simpleRequestProto3()) {
            setOptions(
                clearedFileOptions(
                    clearJavaOuterClassname = true,
                    clearJavaMultipleFiles = true,
                )
            )
        }

        val response = GeneratorRunner.run(req)
        // Filename is "test/echo/echo.proto" -> base "echo" -> derived "Echo".
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
        // "my_great_proto" -> "MyGreatProto" via toCamelCase (split on '_').
        assertThat(response.getFile(0).name).isEqualTo("com/example/echo/MyGreatProtoGrpcKt.kt")
    }

    @Test
    fun `appends OuterClass suffix when derived name matches a message`() {
        // The fixture has a message named EchoRequest. A filename that derives
        // to "EchoRequest" collides with it -- protoc-gen-java appends
        // "OuterClass" and we must do the same so supplier code resolves.
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
        // Cross-check: when the outer class is derived (no java_outer_classname),
        // every reference to it -- supplier bodies, MessageType qualifiers --
        // must use the same derived name.
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
}
