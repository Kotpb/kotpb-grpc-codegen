package io.github.grpckotlin.generator.tests

import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.FileOptions
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto
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
}
