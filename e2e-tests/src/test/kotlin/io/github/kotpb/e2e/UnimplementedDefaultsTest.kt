package io.github.kotpb.e2e

import com.example.proto3_multifiles.EchoRequest
import com.example.proto3_multifiles.EchoServiceGrpcKt
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Verifies that the `NotImplementedError` translation added in `bindService`
 * does not interfere with the default generated `open` method bodies: leaving
 * any RPC un-overridden still answers `UNIMPLEMENTED` with the canonical
 * `"Method <pkg>.<Service>.<Method> is unimplemented"` description.
 */
class UnimplementedDefaultsTest {
    @JvmField
    @RegisterExtension
    val grpc = InProcessGrpcExtension(
        ::EmptyEchoServerImpl,
        EchoServiceGrpcKt::EchoServiceCoroutineStub,
    )

    private val request = EchoRequest.newBuilder().setMessage("x").build()

    @Test
    fun `un-overridden unary method answers UNIMPLEMENTED`() = runTest {
        val status = assertThrows<StatusException> { grpc.stub.unary(request) }.status
        assertThat(status.code).isEqualTo(Status.Code.UNIMPLEMENTED)
        assertThat(status.description).isEqualTo("Method proto3_multifiles.EchoService.Unary is unimplemented")
    }

    @Test
    fun `un-overridden server streaming method answers UNIMPLEMENTED`() = runTest {
        val status = assertThrows<StatusException> { grpc.stub.serverStream(request).toList() }.status
        assertThat(status.code).isEqualTo(Status.Code.UNIMPLEMENTED)
        assertThat(status.description).isEqualTo("Method proto3_multifiles.EchoService.ServerStream is unimplemented")
    }

    @Test
    fun `un-overridden client streaming method answers UNIMPLEMENTED`() = runTest {
        val status = assertThrows<StatusException> { grpc.stub.clientStream(flowOf(request)) }.status
        assertThat(status.code).isEqualTo(Status.Code.UNIMPLEMENTED)
        assertThat(status.description).isEqualTo("Method proto3_multifiles.EchoService.ClientStream is unimplemented")
    }

    @Test
    fun `un-overridden bidi streaming method answers UNIMPLEMENTED`() = runTest {
        val status = assertThrows<StatusException> { grpc.stub.bidiStream(flowOf(request)).toList() }.status
        assertThat(status.code).isEqualTo(Status.Code.UNIMPLEMENTED)
        assertThat(status.description).isEqualTo("Method proto3_multifiles.EchoService.BidiStream is unimplemented")
    }
}

private class EmptyEchoServerImpl : EchoServiceGrpcKt.EchoServiceCoroutineImplBase()
