package io.github.kotpb.generator.tests

import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.FileOptions
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto
import com.google.protobuf.DescriptorProtos.MethodOptions
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto
import com.google.protobuf.DescriptorProtos.ServiceOptions
import com.google.protobuf.DescriptorProtos.SourceCodeInfo
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest

object TestFixtures {
    /**
     * Build a `CodeGeneratorRequest` from [base] but with the first proto file
     * rewritten by [transform], optionally renamed via [withFileName] (which
     * also updates `fileToGenerate` to keep the request valid).
     *
     * Saves callers from the verbose
     * `base.toBuilder().apply { val f = getProtoFile(0); ... clearProtoFile()
     * addProtoFile(...).build()` pattern.
     */
    fun rewriteFile(
        base: CodeGeneratorRequest,
        withFileName: String? = null,
        transform: FileDescriptorProto.Builder.() -> Unit,
    ): CodeGeneratorRequest {
        val builder = base.getProtoFile(0).toBuilder().apply(transform)
        withFileName?.let { builder.name = it }
        val rewritten = builder.build()
        return base.toBuilder().apply {
            clearProtoFile()
            addProtoFile(rewritten)
            withFileName?.let {
                clearFileToGenerate()
                addFileToGenerate(it)
            }
        }.build()
    }

    fun simpleRequestProto3(): CodeGeneratorRequest {
        val service = ServiceDescriptorProto.newBuilder()
            .setName("EchoService")
            .addMethod(echoMethod("Unary"))
            .addMethod(echoMethod("ServerStream", serverStreaming = true))
            .addMethod(echoMethod("ClientStream", clientStreaming = true))
            .addMethod(echoMethod("BidiStream", clientStreaming = true, serverStreaming = true))
            .build()

        val file = FileDescriptorProto.newBuilder()
            .setName("test/echo/echo.proto")
            .setSyntax("proto3")
            .setPackage("test.echo")
            .setOptions(
                FileOptions.newBuilder()
                    .setJavaPackage("com.example.echo")
                    .setJavaOuterClassname("EchoProto")
                    .setJavaMultipleFiles(true)
            )
            .addMessageType(stringMessage("EchoRequest"))
            .addMessageType(stringMessage("EchoResponse"))
            .addService(service)
            .build()

        return CodeGeneratorRequest.newBuilder()
            .addFileToGenerate(file.name)
            .addProtoFile(file)
            .build()
    }

    private fun stringMessage(name: String): DescriptorProto = DescriptorProto.newBuilder()
        .setName(name)
        .addField(
            FieldDescriptorProto.newBuilder()
                .setName("message").setNumber(1)
                .setType(FieldDescriptorProto.Type.TYPE_STRING)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
        )
        .build()

    private fun echoMethod(
        name: String,
        clientStreaming: Boolean = false,
        serverStreaming: Boolean = false,
    ): MethodDescriptorProto = MethodDescriptorProto.newBuilder()
        .setName(name)
        .setInputType(".test.echo.EchoRequest")
        .setOutputType(".test.echo.EchoResponse")
        .setClientStreaming(clientStreaming)
        .setServerStreaming(serverStreaming)
        .build()

    /**
     * Same as [simpleRequestProto3] but the file's `SourceCodeInfo` carries
     * leading comments on the service and on each method, exactly as protoc
     * would emit when reading a `.proto` whose source had them.
     */
    fun requestWithComments(): CodeGeneratorRequest {
        val base = simpleRequestProto3()
        val file = base.getProtoFile(0)

        val sourceInfo = SourceCodeInfo.newBuilder().apply {
            // FileDescriptorProto.service field = 6
            addLocationBuilder().apply {
                addPath(6); addPath(0)
                leadingComments = " EchoService is a simple echo service.\n It echoes back what it receives.\n"
            }
            // service[0].method[0] (Unary) — ServiceDescriptorProto.method = 2
            addLocationBuilder().apply {
                addPath(6); addPath(0); addPath(2); addPath(0)
                leadingComments = " Sends an echo request and waits for the reply.\n"
            }
            // service[0].method[1] (ServerStream)
            addLocationBuilder().apply {
                addPath(6); addPath(0); addPath(2); addPath(1)
                leadingComments = " Streams a sequence of echo replies.\n"
            }
            // service[0].method[2] (ClientStream)
            addLocationBuilder().apply {
                addPath(6); addPath(0); addPath(2); addPath(2)
                leadingComments = " Aggregates a stream of requests into a single reply.\n"
            }
            // service[0].method[3] (BidiStream)
            addLocationBuilder().apply {
                addPath(6); addPath(0); addPath(2); addPath(3)
                leadingComments = " Bidirectional streaming echo.\n"
            }
        }

        val fileWithComments = file.toBuilder().setSourceCodeInfo(sourceInfo).build()
        return base.toBuilder()
            .clearProtoFile()
            .addProtoFile(fileWithComments)
            .build()
    }

    /**
     * Same as [simpleRequestProto3] but with `java_multiple_files` cleared, so
     * messages live nested in the outer Java class and our generator emits a
     * single bundled `<OuterClass>GrpcKt.kt` rather than one file per service.
     */
    fun bundledRequestProto3(): CodeGeneratorRequest {
        val base = simpleRequestProto3()
        val file = base.getProtoFile(0)
        val opts = file.options.toBuilder().clearJavaMultipleFiles().build()
        val rewritten = file.toBuilder().setOptions(opts).build()
        return base.toBuilder().clearProtoFile().addProtoFile(rewritten).build()
    }

    /**
     * Variant whose comments exercise the KDoc-rendering surface:
     *  - multi-line (preserved)
     *  - blank-line paragraph break
     *  - inline backtick code span
     *  - KDoc tag (`@param`)
     *  - KDoc reference link (`[Type]`)
     */
    fun requestWithRichComments(): CodeGeneratorRequest {
        val base = simpleRequestProto3()
        val file = base.getProtoFile(0)

        val richServiceComment = """
             EchoService is the canonical fixture for the codegen pipeline.

             It exists to demonstrate that proto comments survive as KDoc,
             including:

              - multi-line text preserved verbatim
              - paragraph breaks via blank lines
              - inline `code` spans rendered as KDoc backticks
              - references to types like [EchoRequest] and [EchoResponse]
        """.trimIndent().lineSequence().joinToString("\n") { " $it" } + "\n"

        val richMethodComment = """
             Sends an echo request and returns the reply.

             @param request the message to echo
             @return the echoed message wrapped in an EchoResponse
        """.trimIndent().lineSequence().joinToString("\n") { " $it" } + "\n"

        val sourceInfo = SourceCodeInfo.newBuilder().apply {
            addLocationBuilder().apply {
                addPath(6); addPath(0)
                leadingComments = richServiceComment
            }
            addLocationBuilder().apply {
                addPath(6); addPath(0); addPath(2); addPath(0)
                leadingComments = richMethodComment
            }
        }

        val withInfo = file.toBuilder().setSourceCodeInfo(sourceInfo).build()
        return base.toBuilder().clearProtoFile().addProtoFile(withInfo).build()
    }

    /**
     * Variant where the service carries `option deprecated = true` and exactly
     * one of its methods (Unary) is also deprecated. The other three methods
     * remain non-deprecated so we can verify per-method targeting.
     */
    fun requestWithDeprecation(): CodeGeneratorRequest {
        val base = simpleRequestProto3()
        val file = base.getProtoFile(0)
        val service = file.getService(0)

        val methods = service.methodList.mapIndexed { idx, method ->
            if (idx == 0) {
                method.toBuilder()
                    .setOptions(MethodOptions.newBuilder().setDeprecated(true))
                    .build()
            } else {
                method
            }
        }
        val deprecatedService = service.toBuilder()
            .setOptions(ServiceOptions.newBuilder().setDeprecated(true))
            .clearMethod()
            .addAllMethod(methods)
            .build()
        val deprecatedFile = file.toBuilder()
            .clearService()
            .addService(deprecatedService)
            .build()

        return base.toBuilder()
            .clearProtoFile()
            .addProtoFile(deprecatedFile)
            .build()
    }
}
