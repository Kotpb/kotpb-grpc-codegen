package io.github.kotpb.e2e

import com.example.multi_services.Ping
import com.example.multi_services.PingServiceGrpcKt
import com.example.multi_services.Pong
import com.example.multi_services.PongServiceGrpcKt
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies that multiple `service` declarations in a single `.proto` produce
 * coexisting `<Service>GrpcKt` outer objects in one generated `.kt` file, and
 * that both can be served simultaneously from a single in-process channel.
 */
class MultiServicesTest {
    private lateinit var server: io.grpc.Server
    private lateinit var channel: io.grpc.ManagedChannel
    private lateinit var pingStub: PingServiceGrpcKt.PingServiceCoroutineStub
    private lateinit var pongStub: PongServiceGrpcKt.PongServiceCoroutineStub

    @BeforeEach
    fun setUp() {
        val name = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(PingServerImpl())
            .addService(PongServerImpl())
            .build()
            .start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        pingStub = PingServiceGrpcKt.PingServiceCoroutineStub(channel)
        pongStub = PongServiceGrpcKt.PongServiceCoroutineStub(channel)
    }

    @AfterEach
    fun tearDown() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    @Test
    fun `both services in the same file are served from the same channel`() = runTest {
        val pong = pingStub.send(Ping.newBuilder().setFrom("alice").build())
        assertThat(pong.from).isEqualTo("alice-acked")

        val ping = pongStub.send(Pong.newBuilder().setFrom("bob").build())
        assertThat(ping.from).isEqualTo("bob-acked")
    }

    @Test
    fun `each service has its own service descriptor with the canonical FQ name`() {
        assertThat(PingServiceGrpcKt.serviceDescriptor.name)
            .isEqualTo("multi_services.PingService")
        assertThat(PongServiceGrpcKt.serviceDescriptor.name)
            .isEqualTo("multi_services.PongService")
    }

    @Test
    fun `the two service descriptors are distinct instances`() {
        assertThat(PingServiceGrpcKt.serviceDescriptor)
            .isNotSameAs(PongServiceGrpcKt.serviceDescriptor)
    }
}

private class PingServerImpl : PingServiceGrpcKt.PingServiceCoroutineImplBase() {
    override suspend fun send(request: Ping): Pong =
        Pong.newBuilder().setFrom("${request.from}-acked").build()
}

private class PongServerImpl : PongServiceGrpcKt.PongServiceCoroutineImplBase() {
    override suspend fun send(request: Pong): Ping =
        Ping.newBuilder().setFrom("${request.from}-acked").build()
}
