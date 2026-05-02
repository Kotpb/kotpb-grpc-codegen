package io.github.grpckotlin.e2e

import com.example.proto2_multifiles.GreetRequest
import com.example.proto2_multifiles.GreetResponse
import com.example.proto2_multifiles.GreetServiceGrpcKt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class Proto2MultifilesTest {
    @JvmField
    @RegisterExtension
    val grpc = InProcessGrpcExtension(
        ::GreetServerImpl,
        GreetServiceGrpcKt::GreetServiceCoroutineStub,
    )

    @Test
    fun `unary works for proto2 with required fields`() = runTest {
        val response = grpc.stub.greet(
            GreetRequest.newBuilder().setName("Alice").setTitle("Dr.").build()
        )
        assertThat(response.greeting).isEqualTo("Hello, Dr. Alice")
    }

    @Test
    fun `unary works for proto2 without optional title`() = runTest {
        val response = grpc.stub.greet(GreetRequest.newBuilder().setName("Bob").build())
        assertThat(response.greeting).isEqualTo("Hello, Bob")
    }

    @Test
    fun `server streaming works for proto2`() = runTest {
        val responses = grpc.stub.greetMany(
            GreetRequest.newBuilder().setName("World").build()
        ).toList()
        assertThat(responses.map { it.greeting }).hasSize(2)
    }

    @Test
    fun `client streaming works for proto2`() = runTest {
        val requests = flow {
            emit(GreetRequest.newBuilder().setName("a").build())
            emit(GreetRequest.newBuilder().setName("b").build())
        }
        val response = grpc.stub.aggregate(requests)
        assertThat(response.greeting).isEqualTo("Hello, a and b")
    }

    @Test
    fun `bidi streaming works for proto2`() = runTest {
        val requests = flow {
            emit(GreetRequest.newBuilder().setName("p").build())
            emit(GreetRequest.newBuilder().setName("q").build())
        }
        val responses = grpc.stub.chat(requests).toList()
        assertThat(responses.map { it.greeting }).containsExactly("Hi, p", "Hi, q")
    }
}

private class GreetServerImpl : GreetServiceGrpcKt.GreetServiceCoroutineImplBase() {
    override suspend fun greet(request: GreetRequest): GreetResponse {
        val greeting = if (request.hasTitle()) "Hello, ${request.title} ${request.name}"
                       else "Hello, ${request.name}"
        return GreetResponse.newBuilder().setGreeting(greeting).build()
    }

    override fun greetMany(request: GreetRequest): Flow<GreetResponse> = flow {
        emit(GreetResponse.newBuilder().setGreeting("Hi, ${request.name}").build())
        emit(GreetResponse.newBuilder().setGreeting("Bye, ${request.name}").build())
    }

    override suspend fun aggregate(requests: Flow<GreetRequest>): GreetResponse {
        val names = mutableListOf<String>()
        requests.collect { names.add(it.name) }
        return GreetResponse.newBuilder()
            .setGreeting("Hello, ${names.joinToString(" and ")}")
            .build()
    }

    override fun chat(requests: Flow<GreetRequest>): Flow<GreetResponse> = flow {
        requests.collect { req ->
            emit(GreetResponse.newBuilder().setGreeting("Hi, ${req.name}").build())
        }
    }
}
