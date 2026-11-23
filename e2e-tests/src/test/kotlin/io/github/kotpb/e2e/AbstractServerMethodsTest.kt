package io.github.kotpb.e2e

import com.example.abstract_server_methods.GreetRequest
import com.example.abstract_server_methods.GreetResponse
import com.example.abstract_server_methods.GreeterServiceGrpcKt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.lang.reflect.Modifier

/**
 * Matrix cell: `--grpc-kotlin_opt=abstract_server_methods`. The server below has to override every RPC, the deprecated
 * one too, or this file fails to compile; and it serves calls just as one on an open base does.
 */
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
