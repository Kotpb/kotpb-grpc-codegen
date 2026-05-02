package io.github.grpckotlin.generator.tests

import io.github.grpckotlin.generator.GeneratorRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneratedShapeTest {
    private val content: String by lazy {
        GeneratorRunner.run(TestFixtures.simpleRequestProto3()).getFile(0).content
    }

    @Test
    fun `output file declares the right package`() {
        assertThat(content).contains("package com.example.echo")
    }

    @Test
    fun `output file declares the SERVICE_NAME constant`() {
        assertThat(content)
            .contains("public const val SERVICE_NAME: String = \"test.echo.EchoService\"")
    }

    @Test
    fun `output file declares the outer object`() {
        assertThat(content).contains("public object EchoServiceGrpcKt")
    }

    @Test
    fun `output file declares CoroutineStub class`() {
        assertThat(content).contains("class EchoServiceCoroutineStub")
        assertThat(content).contains("AbstractCoroutineStub<EchoServiceCoroutineStub>")
    }

    @Test
    fun `output file declares CoroutineImplBase class`() {
        assertThat(content).contains("abstract class EchoServiceCoroutineImplBase")
        assertThat(content).contains(": AbstractCoroutineServerImpl")
        assertThat(content).contains("BindableService")
    }

    @Test
    fun `unary client method is suspend and returns response type`() {
        assertThat(content).contains("public suspend fun unary(")
        assertThat(content).contains("request: EchoRequest")
        assertThat(content).contains("): EchoResponse")
    }

    @Test
    fun `server-streaming client method returns Flow and is not suspend`() {
        assertThat(content).contains("public fun serverStream(")
        assertThat(content).contains("): Flow<EchoResponse>")
    }

    @Test
    fun `client-streaming client method takes Flow request and is suspend`() {
        assertThat(content).contains("public suspend fun clientStream(")
        assertThat(content).contains("requests: Flow<EchoRequest>")
    }

    @Test
    fun `bidi client method takes Flow and returns Flow`() {
        assertThat(content).contains("public fun bidiStream(")
        assertThat(content).contains("requests: Flow<EchoRequest>")
        assertThat(content).contains("Flow<EchoResponse>")
    }

    @Test
    fun `MethodDescriptor uses ProtoUtils marshaller and self-built descriptor`() {
        assertThat(content).contains("newBuilder<EchoRequest, EchoResponse>()")
        assertThat(content).contains("ProtoUtils.marshaller(EchoRequest.getDefaultInstance())")
        assertThat(content).contains("ProtoUtils.marshaller(EchoResponse.getDefaultInstance())")
        assertThat(content).contains("generateFullMethodName(SERVICE_NAME, \"Unary\")")
    }

    @Test
    fun `ServiceDescriptor is built from our own suppliers and addMethod calls`() {
        assertThat(content).contains("ServiceDescriptor.newBuilder(SERVICE_NAME)")
        // EchoServiceFileDescriptorSupplier is now an `object`, so the schema
        // descriptor reference is the singleton itself -- no parens, no allocation.
        assertThat(content).contains("setSchemaDescriptor(EchoServiceFileDescriptorSupplier)")
        assertThat(content).contains("addMethod(getUnaryMethod)")
        assertThat(content).contains("addMethod(getServerStreamMethod)")
        assertThat(content).contains("addMethod(getClientStreamMethod)")
        assertThat(content).contains("addMethod(getBidiStreamMethod)")
    }

    @Test
    fun `descriptor suppliers reference the protoc-gen-java outer class`() {
        assertThat(content).contains("EchoProto.getDescriptor()")
        assertThat(content).contains("findServiceByName(\"EchoService\")")
    }

    @Test
    fun `bindService uses ServerCalls and matching method definition factories`() {
        assertThat(content).contains("ServerServiceDefinition.builder(serviceDescriptor)")
        assertThat(content).contains("ServerCalls.unaryServerMethodDefinition(context, getUnaryMethod, ::unary)")
        assertThat(content).contains(
            "ServerCalls.serverStreamingServerMethodDefinition(context, getServerStreamMethod, ::serverStream)"
        )
        assertThat(content).contains(
            "ServerCalls.clientStreamingServerMethodDefinition(context, getClientStreamMethod, ::clientStream)"
        )
        assertThat(content).contains(
            "ServerCalls.bidiStreamingServerMethodDefinition(context, getBidiStreamMethod, ::bidiStream)"
        )
    }

    @Test
    fun `server unary method throws UNIMPLEMENTED by default`() {
        assertThat(content).contains("Status.UNIMPLEMENTED.withDescription")
        assertThat(content).contains("test.echo.EchoService.Unary")
    }
}
