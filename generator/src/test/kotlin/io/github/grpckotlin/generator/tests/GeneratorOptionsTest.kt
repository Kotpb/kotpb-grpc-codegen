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
    fun `falls back to proto package when java_package is absent`() {
        // Default fixture sets java_package=com.example.echo. Clear it and the
        // generator should fall back to the proto package (test.echo).
        val req = TestFixtures.simpleRequestProto3().toBuilder().apply {
            val original = getProtoFile(0)
            val opts = original.options.toBuilder().clearJavaPackage().build()
            clearProtoFile()
            addProtoFile(original.toBuilder().setOptions(opts).build())
        }.build()

        val response = GeneratorRunner.run(req)
        assertThat(response.getFile(0).name).startsWith("test/echo/")
        assertThat(response.getFile(0).content).contains("package test.echo")
    }

    @Test
    fun `derives outer class name from filename when java_outer_classname is absent`() {
        // Bundled mode (clear java_multiple_files) so the outer class name
        // appears in the generated file's name.
        val req = TestFixtures.simpleRequestProto3().toBuilder().apply {
            val original = getProtoFile(0)
            val opts = original.options.toBuilder()
                .clearJavaOuterClassname()
                .clearJavaMultipleFiles()
                .build()
            clearProtoFile()
            addProtoFile(original.toBuilder().setOptions(opts).build())
        }.build()

        val response = GeneratorRunner.run(req)
        // Filename is "test/echo/echo.proto" -> base "echo" -> derived "Echo".
        assertThat(response.getFile(0).name).isEqualTo("com/example/echo/EchoGrpcKt.kt")
    }

    @Test
    fun `derives PascalCase outer class from snake_case filename`() {
        val newName = "test/echo/my_great_proto.proto"
        val req = TestFixtures.simpleRequestProto3().toBuilder().apply {
            val original = getProtoFile(0)
            val opts = original.options.toBuilder()
                .clearJavaOuterClassname()
                .clearJavaMultipleFiles()
                .build()
            clearProtoFile()
            addProtoFile(original.toBuilder().setName(newName).setOptions(opts).build())
            clearFileToGenerate()
            addFileToGenerate(newName)
        }.build()

        val response = GeneratorRunner.run(req)
        // "my_great_proto" -> "MyGreatProto" via toCamelCase (split on '_').
        assertThat(response.getFile(0).name).isEqualTo("com/example/echo/MyGreatProtoGrpcKt.kt")
    }

    @Test
    fun `appends OuterClass suffix when derived name matches a message`() {
        // The fixture already has a message named EchoRequest. A filename
        // that derives to "EchoRequest" collides with it -- protoc-gen-java
        // appends "OuterClass" to the outer class name and we must do the
        // same so generated supplier code references the same class.
        val newName = "test/echo/echo_request.proto"
        val req = TestFixtures.simpleRequestProto3().toBuilder().apply {
            val original = getProtoFile(0)
            val opts = original.options.toBuilder()
                .clearJavaOuterClassname()
                .clearJavaMultipleFiles()
                .build()
            clearProtoFile()
            addProtoFile(original.toBuilder().setName(newName).setOptions(opts).build())
            clearFileToGenerate()
            addFileToGenerate(newName)
        }.build()

        val response = GeneratorRunner.run(req)
        assertThat(response.getFile(0).name)
            .isEqualTo("com/example/echo/EchoRequestOuterClassGrpcKt.kt")
    }

    @Test
    fun `appends OuterClass suffix when derived name matches the service`() {
        // Filename derives to "EchoService", which is the service name.
        val newName = "test/echo/echo_service.proto"
        val req = TestFixtures.simpleRequestProto3().toBuilder().apply {
            val original = getProtoFile(0)
            val opts = original.options.toBuilder()
                .clearJavaOuterClassname()
                .clearJavaMultipleFiles()
                .build()
            clearProtoFile()
            addProtoFile(original.toBuilder().setName(newName).setOptions(opts).build())
            clearFileToGenerate()
            addFileToGenerate(newName)
        }.build()

        val response = GeneratorRunner.run(req)
        assertThat(response.getFile(0).name)
            .isEqualTo("com/example/echo/EchoServiceOuterClassGrpcKt.kt")
    }

    @Test
    fun `bundled mode references derived outer class in supplier and message types`() {
        // Cross-check: when the outer class is derived (no java_outer_classname),
        // every reference to it -- in supplier bodies, in MessageType.<...>
        // qualifiers -- must use the same derived name.
        val req = TestFixtures.simpleRequestProto3().toBuilder().apply {
            val original = getProtoFile(0)
            val opts = original.options.toBuilder()
                .clearJavaOuterClassname()
                .clearJavaMultipleFiles()
                .build()
            clearProtoFile()
            addProtoFile(original.toBuilder().setOptions(opts).build())
        }.build()

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
