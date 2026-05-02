package io.github.grpckotlin.e2e

import com.example.outer_class_collision.NoteServiceGrpcKt
import com.example.outer_class_collision.OuterClassCollisionOuterClass
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Matrix cell: derived outer class name collides with a top-level message.
 *
 * If our `DescriptorUtil.resolveJavaOuterClassName` ever stopped applying
 * the "append OuterClass on collision" rule, the import below
 * (`OuterClassCollisionOuterClass`) would not resolve and this test would
 * fail to compile -- because that's the exact class name protoc-gen-java
 * generates. Compilation alone is the strongest signal here; the runtime
 * RPC adds a second check that the file descriptor lookup inside the
 * generated supplier finds the right class.
 */
class OuterClassCollisionTest {
    @JvmField
    @RegisterExtension
    val grpc = InProcessGrpcExtension(
        ::NoteServerImpl,
        NoteServiceGrpcKt::NoteServiceCoroutineStub,
    )

    @Test
    fun `RPC works when the outer class name was promoted by the collision rule`() = runTest {
        val request = OuterClassCollisionOuterClass.OuterClassCollision.newBuilder()
            .setNote("hello")
            .build()
        val response = grpc.stub.take(request)
        assertThat(response.note).isEqualTo("noted: hello")
    }

    @Test
    fun `serviceDescriptor's schema descriptor resolves through the promoted outer class`() {
        // The supplier inside our generated code calls
        // `OuterClassCollisionOuterClass.getDescriptor()`. Reading the
        // descriptor proves that lookup actually returns a non-null
        // FileDescriptor and our supplier wired to the right Java class.
        val descriptor = NoteServiceGrpcKt.serviceDescriptor
        assertThat(descriptor.name).isEqualTo("outer_class_collision.NoteService")
    }
}

private class NoteServerImpl : NoteServiceGrpcKt.NoteServiceCoroutineImplBase() {
    override suspend fun take(
        request: OuterClassCollisionOuterClass.OuterClassCollision,
    ): OuterClassCollisionOuterClass.OuterClassCollision =
        OuterClassCollisionOuterClass.OuterClassCollision.newBuilder()
            .setNote("noted: ${request.note}")
            .build()
}
