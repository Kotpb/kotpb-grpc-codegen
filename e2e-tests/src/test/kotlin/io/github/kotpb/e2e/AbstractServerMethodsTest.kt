package io.github.kotpb.e2e

import com.example.abstract_server_methods.GreetRequest
import com.example.abstract_server_methods.GreetResponse
import com.example.abstract_server_methods.GreeterServiceGrpcKt
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.lang.reflect.Modifier

class AbstractServerMethodsTest {
    @JvmField
    @RegisterExtension
    val grpc = InProcessGrpcExtension(
        ::GreeterServerImpl,
        GreeterServiceGrpcKt::GreeterServiceCoroutineStub,
    )

    private fun request(name: String) = GreetRequest.newBuilder().setName(name).build()

    @Test
    fun `every RPC of the impl base is abstract`() {
        val base = GreeterServiceGrpcKt.GreeterServiceCoroutineImplBase::class.java
        val rpcs = listOf("greet", "greetMany", "greetAll", "greetEach", "oldGreet")
        for (name in rpcs) {
            val method = base.declaredMethods.single { it.name == name }
            assertThat(Modifier.isAbstract(method.modifiers)).describedAs(name).isTrue()
        }
    }

    @Test
    fun `each kind of RPC round trips`() = runTest {
        assertThat(grpc.stub.greet(request("a")).message).isEqualTo("hello a")
        assertThat(grpc.stub.greetMany(request("b")).toList().map { it.message }).containsExactly("hello b", "hi b")
        assertThat(grpc.stub.greetAll(flowOf(request("c"), request("d"))).message).isEqualTo("hello c,d")
        assertThat(grpc.stub.greetEach(flowOf(request("e"))).toList().map { it.message }).containsExactly("hello e")
    }

    @Test
    @Suppress("DEPRECATION")
    fun `a deprecated RPC is implemented and served like the others`() = runTest {
        assertThat(grpc.stub.oldGreet(request("f")).message).isEqualTo("old hello f")
    }

    @Test
    @Suppress("DEPRECATION")
    fun `TODO overrides in an abstract server answer UNIMPLEMENTED`() = runTest {
        val name = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(TodoGreeterServerImpl())
            .build()
            .start()
        val channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        try {
            val stub = GreeterServiceGrpcKt.GreeterServiceCoroutineStub(channel)
            assertUnimplemented("Greet") { stub.greet(request("a")) }
            assertUnimplemented("GreetMany") { stub.greetMany(request("b")).toList() }
            assertUnimplemented("GreetAll") { stub.greetAll(flowOf(request("c"))) }
            assertUnimplemented("GreetEach") { stub.greetEach(flowOf(request("d"))).toList() }
            assertUnimplemented("OldGreet") { stub.oldGreet(request("e")) }
        } finally {
            channel.shutdownNow()
            server.shutdownNow()
        }
    }

    private suspend fun assertUnimplemented(methodName: String, call: suspend () -> Any?) {
        val status = assertThrows<StatusException> { call() }.status
        assertThat(status.code).isEqualTo(Status.Code.UNIMPLEMENTED)
        assertThat(status.description).isEqualTo(
            "Method abstract_server_methods.GreeterService.$methodName is unimplemented"
        )
    }
}

private fun response(message: String) = GreetResponse.newBuilder().setMessage(message).build()

private class GreeterServerImpl : GreeterServiceGrpcKt.GreeterServiceCoroutineImplBase() {
    override suspend fun greet(request: GreetRequest): GreetResponse = response("hello ${request.name}")

    override fun greetMany(request: GreetRequest): Flow<GreetResponse> = flow {
        emit(response("hello ${request.name}"))
        emit(response("hi ${request.name}"))
    }

    override suspend fun greetAll(requests: Flow<GreetRequest>): GreetResponse =
        response("hello ${requests.toList().joinToString(",") { it.name }}")

    override fun greetEach(requests: Flow<GreetRequest>): Flow<GreetResponse> =
        requests.map { response("hello ${it.name}") }

    @Deprecated("This RPC is deprecated.")
    override suspend fun oldGreet(request: GreetRequest): GreetResponse = response("old hello ${request.name}")
}

private class TodoGreeterServerImpl : GreeterServiceGrpcKt.GreeterServiceCoroutineImplBase() {
    override suspend fun greet(request: GreetRequest): GreetResponse = TODO()

    override fun greetMany(request: GreetRequest): Flow<GreetResponse> = TODO()

    override suspend fun greetAll(requests: Flow<GreetRequest>): GreetResponse = TODO("not yet")

    override fun greetEach(requests: Flow<GreetRequest>): Flow<GreetResponse> = flow { TODO("not yet") }

    @Deprecated("This RPC is deprecated.")
    override suspend fun oldGreet(request: GreetRequest): GreetResponse = TODO()
}
