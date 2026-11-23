package io.github.kotpb.e2e

import com.example.proto3_multifiles.EchoRequest
import com.example.proto3_multifiles.EchoResponse
import com.example.proto3_multifiles.EchoServiceGrpcKt
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * A method stubbed with Kotlin's `TODO()` throws [NotImplementedError], which grpc-kotlin-stub would answer with
 * `UNKNOWN`, like any exception that isn't a gRPC status. The generated `bindService` answers it with `UNIMPLEMENTED`
 * instead, as the default body does for a method left out. A streaming method can throw it when called, as in
 * `= TODO()`, or as its flow is collected, as in `flow { TODO() }`: the fixture does the first for server streaming and
 * the second for bidi.
 */
class NotImplementedErrorTest {
    @JvmField
    @RegisterExtension
    val grpc = InProcessGrpcExtension(
        ::TodoEchoServerImpl,
        EchoServiceGrpcKt::EchoServiceCoroutineStub,
    )

    private val request = EchoRequest.newBuilder().setMessage("x").build()

    @Test
    fun `unary method that calls TODO() answers UNIMPLEMENTED`() = runTest {
        val status = assertThrows<StatusException> { grpc.stub.unary(request) }.status
        assertThat(status.code).isEqualTo(Status.Code.UNIMPLEMENTED)
        assertThat(status.description).isEqualTo("Method proto3_multifiles.EchoService.Unary is unimplemented")
    }

    @Test
    fun `server streaming method that is TODO() answers UNIMPLEMENTED`() = runTest {
        val status = assertThrows<StatusException> { grpc.stub.serverStream(request).toList() }.status
        assertThat(status.code).isEqualTo(Status.Code.UNIMPLEMENTED)
    }

    @Test
    fun `client streaming method that calls TODO() answers UNIMPLEMENTED`() = runTest {
        val status = assertThrows<StatusException> { grpc.stub.clientStream(flowOf(request)) }.status
        assertThat(status.code).isEqualTo(Status.Code.UNIMPLEMENTED)
    }

    @Test
    fun `bidi streaming flow that calls TODO() as it is collected answers UNIMPLEMENTED`() = runTest {
        val status = assertThrows<StatusException> { grpc.stub.bidiStream(flowOf(request)).toList() }.status
        assertThat(status.code).isEqualTo(Status.Code.UNIMPLEMENTED)
        assertThat(status.description).isEqualTo("Method proto3_multifiles.EchoService.BidiStream is unimplemented")
    }
}

private class TodoEchoServerImpl : EchoServiceGrpcKt.EchoServiceCoroutineImplBase() {
    override suspend fun unary(request: EchoRequest): EchoResponse = TODO()

    override fun serverStream(request: EchoRequest): Flow<EchoResponse> = TODO()

    override suspend fun clientStream(requests: Flow<EchoRequest>): EchoResponse = TODO("not yet")

    override fun bidiStream(requests: Flow<EchoRequest>): Flow<EchoResponse> = flow { TODO("not yet") }
}
