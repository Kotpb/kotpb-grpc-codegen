package io.github.grpckotlin.generator.tests

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
    fun simpleRequestProto3(): CodeGeneratorRequest {
        val echoRequest = DescriptorProto.newBuilder()
            .setName("EchoRequest")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("message").setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            )
            .build()
        val echoResponse = DescriptorProto.newBuilder()
            .setName("EchoResponse")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("message").setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            )
            .build()

        val service = ServiceDescriptorProto.newBuilder()
            .setName("EchoService")
            .addMethod(
                MethodDescriptorProto.newBuilder()
                    .setName("Unary")
                    .setInputType(".test.echo.EchoRequest")
                    .setOutputType(".test.echo.EchoResponse")
            )
            .addMethod(
                MethodDescriptorProto.newBuilder()
                    .setName("ServerStream")
                    .setInputType(".test.echo.EchoRequest")
                    .setOutputType(".test.echo.EchoResponse")
                    .setServerStreaming(true)
            )
            .addMethod(
                MethodDescriptorProto.newBuilder()
                    .setName("ClientStream")
                    .setInputType(".test.echo.EchoRequest")
                    .setOutputType(".test.echo.EchoResponse")
                    .setClientStreaming(true)
            )
            .addMethod(
                MethodDescriptorProto.newBuilder()
                    .setName("BidiStream")
                    .setInputType(".test.echo.EchoRequest")
                    .setOutputType(".test.echo.EchoResponse")
                    .setClientStreaming(true)
                    .setServerStreaming(true)
            )
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
            .addMessageType(echoRequest)
            .addMessageType(echoResponse)
            .addService(service)
            .build()

        return CodeGeneratorRequest.newBuilder()
            .addFileToGenerate(file.name)
            .addProtoFile(file)
            .build()
    }

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
