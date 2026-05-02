package io.github.grpckotlin.e2e

import com.example.editions2024.ChatAck
import com.example.editions2024.ChatMessage
import com.example.editions2024.ChatServiceGrpcKt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class Editions2024Test {
    @JvmField
    @RegisterExtension
    val grpc = InProcessGrpcExtension(
        ::ChatServerImpl,
        ChatServiceGrpcKt::ChatServiceCoroutineStub,
    )

    @Test
    fun `unary send for edition 2024`() = runTest {
        val ack = grpc.stub.send(
            ChatMessage.newBuilder().setUser("alice").setText("hi").build()
        )
        assertThat(ack.id).isEqualTo(1L)
    }

    @Test
    fun `server streaming subscribe for edition 2024`() = runTest {
        val messages = grpc.stub.subscribe(ChatAck.newBuilder().setId(0).build()).toList()
        assertThat(messages.map { it.text }).containsExactly("welcome", "still here", "goodbye")
    }

    @Test
    fun `client streaming bulk for edition 2024`() = runTest {
        val msgs = flow {
            emit(ChatMessage.newBuilder().setUser("a").setText("1").build())
            emit(ChatMessage.newBuilder().setUser("b").setText("2").build())
            emit(ChatMessage.newBuilder().setUser("c").setText("3").build())
        }
        val ack = grpc.stub.bulk(msgs)
        assertThat(ack.id).isEqualTo(3L)
    }

    @Test
    fun `bidi live for edition 2024`() = runTest {
        val msgs = flow {
            emit(ChatMessage.newBuilder().setUser("alice").setText("ping").build())
            emit(ChatMessage.newBuilder().setUser("bob").setText("pong").build())
        }
        val responses = grpc.stub.live(msgs).toList()
        assertThat(responses.map { it.text }).containsExactly("echo:ping", "echo:pong")
        assertThat(responses.map { it.user }).containsExactly("server", "server")
    }
}

private class ChatServerImpl : ChatServiceGrpcKt.ChatServiceCoroutineImplBase() {
    override suspend fun send(request: ChatMessage): ChatAck =
        ChatAck.newBuilder().setId(1L).build()

    override fun subscribe(request: ChatAck): Flow<ChatMessage> = flow {
        listOf("welcome", "still here", "goodbye").forEach {
            emit(ChatMessage.newBuilder().setUser("server").setText(it).build())
        }
    }

    override suspend fun bulk(requests: Flow<ChatMessage>): ChatAck {
        var count = 0L
        requests.collect { count++ }
        return ChatAck.newBuilder().setId(count).build()
    }

    override fun live(requests: Flow<ChatMessage>): Flow<ChatMessage> = flow {
        requests.collect { req ->
            emit(ChatMessage.newBuilder().setUser("server").setText("echo:${req.text}").build())
        }
    }
}
