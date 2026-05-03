package io.github.kotpb.e2e

import com.example.proto3_multifiles.EchoRequest
import com.example.proto3_multifiles.EchoResponse
import com.example.proto3_multifiles.EchoServiceGrpcKt
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.kotlin.ServerCalls
import io.grpc.protobuf.ProtoUtils
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Wire-level interop: prove that our generated stubs/servers use the canonical
 * fullMethodName / wire format that any grpc-java-built peer expects. We do this
 * by hand-building a MethodDescriptor (no involvement from our generator) and
 * driving it against our generated counterpart.
 *
 * Wire format identity is the actual interop guarantee for RPCs across the network,
 * regardless of which generator built either side.
 */
class WireInteropTest {
    private lateinit var server: io.grpc.Server
    private lateinit var channel: io.grpc.ManagedChannel

    private val canonicalServiceName = "proto3_multifiles.EchoService"
    private val canonicalUnaryFullMethodName = "$canonicalServiceName/Unary"

    @AfterEach
    fun tearDown() {
        if (::channel.isInitialized) channel.shutdownNow()
        if (::server.isInitialized) server.shutdownNow()
    }

    @Test
    fun `our generated client can call a hand-built grpc-java-shape server`() = runTest {
        val handBuiltMethod = MethodDescriptor.newBuilder<EchoRequest, EchoResponse>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(canonicalUnaryFullMethodName)
            .setRequestMarshaller(ProtoUtils.marshaller(EchoRequest.getDefaultInstance()))
            .setResponseMarshaller(ProtoUtils.marshaller(EchoResponse.getDefaultInstance()))
            .build()

        val handBuiltService = ServerServiceDefinition.builder(canonicalServiceName)
            .addMethod(
                ServerCalls.unaryServerMethodDefinition(
                    context = EmptyCoroutineContext,
                    descriptor = handBuiltMethod,
                ) { request ->
                    EchoResponse.newBuilder()
                        .setMessage("interop:${request.message}")
                        .build()
                }
            )
            .build()

        val name = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(handBuiltService)
            .build().start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        val ourStub = EchoServiceGrpcKt.EchoServiceCoroutineStub(channel)

        val response = ourStub.unary(EchoRequest.newBuilder().setMessage("ping").build())
        assertThat(response.message).isEqualTo("interop:ping")
    }

    @Test
    fun `a hand-built client using the canonical fullMethodName can call our generated server`() = runTest {
        val handBuiltMethod = MethodDescriptor.newBuilder<EchoRequest, EchoResponse>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(canonicalUnaryFullMethodName)
            .setRequestMarshaller(ProtoUtils.marshaller(EchoRequest.getDefaultInstance()))
            .setResponseMarshaller(ProtoUtils.marshaller(EchoResponse.getDefaultInstance()))
            .build()

        val name = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(InteropEchoServer())
            .build().start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()

        val response = io.grpc.kotlin.ClientCalls.unaryRpc(
            channel = channel,
            method = handBuiltMethod,
            request = EchoRequest.newBuilder().setMessage("hi").build(),
        )
        assertThat(response.message).isEqualTo("ours:hi")
    }

    @Test
    fun `our generated MethodDescriptor uses the canonical fullMethodName format`() {
        val descriptor = EchoServiceGrpcKt.getUnaryMethod
        assertThat(descriptor.fullMethodName).isEqualTo(canonicalUnaryFullMethodName)
        assertThat(descriptor.type).isEqualTo(MethodDescriptor.MethodType.UNARY)
        assertThat(descriptor.serviceName).isEqualTo(canonicalServiceName)
        assertThat(descriptor.bareMethodName).isEqualTo("Unary")
    }

    @Test
    fun `our generated ServiceDescriptor exposes the right service name and method count`() {
        val descriptor = EchoServiceGrpcKt.serviceDescriptor
        assertThat(descriptor.name).isEqualTo(canonicalServiceName)
        assertThat(descriptor.methods.map { it.bareMethodName })
            .containsExactlyInAnyOrder("Unary", "ServerStream", "ClientStream", "BidiStream")
    }

    @Test
    fun `stub companion exposes the same serviceDescriptor as the outer GrpcKt`() {
        assertThat(EchoServiceGrpcKt.EchoServiceCoroutineStub.serviceDescriptor)
            .isSameAs(EchoServiceGrpcKt.serviceDescriptor)
    }

    @Test
    fun `impl-base companion exposes the same serviceDescriptor as the outer GrpcKt`() {
        assertThat(EchoServiceGrpcKt.EchoServiceCoroutineImplBase.serviceDescriptor)
            .isSameAs(EchoServiceGrpcKt.serviceDescriptor)
    }
}

private class InteropEchoServer : EchoServiceGrpcKt.EchoServiceCoroutineImplBase() {
    override suspend fun unary(request: EchoRequest): EchoResponse =
        EchoResponse.newBuilder().setMessage("ours:${request.message}").build()
}
