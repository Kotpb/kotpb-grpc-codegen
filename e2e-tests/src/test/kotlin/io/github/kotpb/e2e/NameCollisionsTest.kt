package io.github.kotpb.e2e

import collision.Collision
import collision.CollisionServiceGrpcKt
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Regression test for grpc/grpc-kotlin#467 — ensures the generator handles
 * worst-case name collisions (package == message == method == field, all
 * sharing `collision`/`Collision`). If any reference in the generated `.kt`
 * file lacked a proper qualifier, this test would either fail to compile
 * or the RPC would be wired to the wrong descriptor at runtime.
 */
class NameCollisionsTest {
    @JvmField
    @RegisterExtension
    val grpc = InProcessGrpcExtension(
        ::CollisionServerImpl,
        CollisionServiceGrpcKt::CollisionServiceCoroutineStub,
    )

    @Test
    fun `RPC round-trip works under maximum name collision`() = runTest {
        val response = grpc.stub.collision(
            Collision.newBuilder().setCollision("ping").build()
        )
        assertThat(response.collision).isEqualTo("pong:ping")
    }

    @Test
    fun `serviceDescriptor accessor still resolves to the unique instance`() {
        assertThat(CollisionServiceGrpcKt.CollisionServiceCoroutineStub.serviceDescriptor)
            .isSameAs(CollisionServiceGrpcKt.serviceDescriptor)
        assertThat(CollisionServiceGrpcKt.CollisionServiceCoroutineImplBase.serviceDescriptor)
            .isSameAs(CollisionServiceGrpcKt.serviceDescriptor)
        assertThat(CollisionServiceGrpcKt.serviceDescriptor.name)
            .isEqualTo("collision.CollisionService")
    }

    @Test
    fun `MethodDescriptor full method name composes package, service, and method correctly`() {
        // Method name `Collision` shadows the message type name in plain text;
        // the descriptor must still report the canonical fullMethodName.
        val md = CollisionServiceGrpcKt.getCollisionMethod
        assertThat(md.fullMethodName).isEqualTo("collision.CollisionService/Collision")
        assertThat(md.serviceName).isEqualTo("collision.CollisionService")
        assertThat(md.bareMethodName).isEqualTo("Collision")
    }
}

private class CollisionServerImpl : CollisionServiceGrpcKt.CollisionServiceCoroutineImplBase() {
    override suspend fun collision(request: Collision): Collision =
        Collision.newBuilder().setCollision("pong:${request.collision}").build()
}
