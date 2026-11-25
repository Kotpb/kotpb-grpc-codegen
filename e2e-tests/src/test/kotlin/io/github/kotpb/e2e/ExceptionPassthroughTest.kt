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
 * Verifies that `bindService`'s `NotImplementedError` wrapper passes every
 * other exception through untouched — both `StatusException`s and non-status
 * exceptions, whether thrown directly or during `Flow` collection.
 */
class ExceptionPassthroughTest {
    @JvmField
    @RegisterExtension
    val grpc = InProcessGrpcExtension(
        ::ThrowingEchoServerImpl,
        EchoServiceGrpcKt::EchoServiceCoroutineStub,
    )

    private val request = EchoRequest.newBuilder().setMessage("x").build()

    @Test
    fun `StatusException thrown directly by unary method passes through`() = runTest {
        val status = assertThrows<StatusException> { grpc.stub.unary(request) }.status
        assertThat(status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
        assertThat(status.description).isEqualTo("bad unary")
    }

    @Test
    fun `StatusException thrown inside streaming flow passes through`() = runTest {
        val status = assertThrows<StatusException> { grpc.stub.serverStream(request).toList() }.status
        assertThat(status.code).isEqualTo(Status.Code.FAILED_PRECONDITION)
        assertThat(status.description).isEqualTo("bad stream")
    }

    @Test
    fun `non-status exception thrown directly still maps to UNKNOWN`() = runTest {
        val status = assertThrows<StatusException> { grpc.stub.clientStream(flowOf(request)) }.status
        assertThat(status.code).isEqualTo(Status.Code.UNKNOWN)
    }

    @Test
    fun `non-status exception thrown inside streaming flow still maps to UNKNOWN`() = runTest {
        val status = assertThrows<StatusException> { grpc.stub.bidiStream(flowOf(request)).toList() }.status
        assertThat(status.code).isEqualTo(Status.Code.UNKNOWN)
    }
}

private class ThrowingEchoServerImpl : EchoServiceGrpcKt.EchoServiceCoroutineImplBase() {
    override suspend fun unary(request: EchoRequest): EchoResponse =
        throw StatusException(Status.INVALID_ARGUMENT.withDescription("bad unary"))

    override fun serverStream(request: EchoRequest): Flow<EchoResponse> = flow {
        throw StatusException(Status.FAILED_PRECONDITION.withDescription("bad stream"))
    }

    override suspend fun clientStream(requests: Flow<EchoRequest>): EchoResponse =
        error("client boom")

    override fun bidiStream(requests: Flow<EchoRequest>): Flow<EchoResponse> = flow {
        error("bidi boom")
    }
}
