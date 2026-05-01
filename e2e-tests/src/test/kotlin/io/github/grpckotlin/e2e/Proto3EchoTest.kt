package io.github.grpckotlin.e2e

import com.example.echo.EchoRequest
import com.example.echo.EchoResponse
import com.example.echo.EchoServiceGrpcKt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class Proto3EchoTest {
    @JvmField
    @RegisterExtension
    val grpc = InProcessGrpcExtension(
        ::EchoServerImpl,
        EchoServiceGrpcKt::EchoServiceCoroutineStub,
    )

    @Test
    fun `unary RPC round trip`() = runTest {
        val response = grpc.stub.unary(EchoRequest.newBuilder().setMessage("hello").build())
        assertThat(response.message).isEqualTo("echo: hello")
    }

    @Test
    fun `server streaming yields multiple responses`() = runTest {
        val responses = grpc.stub.serverStream(
            EchoRequest.newBuilder().setMessage("x").build()
        ).toList()
        assertThat(responses.map { it.message }).containsExactly("x-0", "x-1", "x-2")
        assertThat(responses.map { it.sequence }).containsExactly(0, 1, 2)
    }

    @Test
    fun `client streaming aggregates request stream`() = runTest {
        val requests = flow {
            emit(EchoRequest.newBuilder().setMessage("a").build())
            emit(EchoRequest.newBuilder().setMessage("b").build())
            emit(EchoRequest.newBuilder().setMessage("c").build())
        }
        val response = grpc.stub.clientStream(requests)
        assertThat(response.message).isEqualTo("a,b,c")
    }

    @Test
    fun `bidi streaming produces one response per request`() = runTest {
        val requests = flow {
            emit(EchoRequest.newBuilder().setMessage("p").build())
            emit(EchoRequest.newBuilder().setMessage("q").build())
        }
        val responses = grpc.stub.bidiStream(requests).toList()
        assertThat(responses.map { it.message }).containsExactly("bidi:p", "bidi:q")
    }
}

private class EchoServerImpl : EchoServiceGrpcKt.EchoServiceCoroutineImplBase() {
    override suspend fun unary(request: EchoRequest): EchoResponse =
        EchoResponse.newBuilder().setMessage("echo: ${request.message}").build()

    override fun serverStream(request: EchoRequest): Flow<EchoResponse> = flow {
        repeat(3) { i ->
            emit(
                EchoResponse.newBuilder()
                    .setMessage("${request.message}-$i")
                    .setSequence(i)
                    .build()
            )
        }
    }

    override suspend fun clientStream(requests: Flow<EchoRequest>): EchoResponse {
        val joined = mutableListOf<String>()
        requests.collect { joined.add(it.message) }
        return EchoResponse.newBuilder().setMessage(joined.joinToString(",")).build()
    }

    override fun bidiStream(requests: Flow<EchoRequest>): Flow<EchoResponse> = flow {
        requests.collect { req ->
            emit(EchoResponse.newBuilder().setMessage("bidi:${req.message}").build())
        }
    }
}
