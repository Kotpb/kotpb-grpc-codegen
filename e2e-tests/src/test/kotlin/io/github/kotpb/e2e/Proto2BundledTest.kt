package io.github.kotpb.e2e

import com.example.proto2_bundled.Proto2BundledProto
import com.example.proto2_bundled.RegisterServiceGrpcKt
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Matrix cell: proto2 with `java_multiple_files = false` (default).
 *
 * Combines the proto2 syntax line (with required/optional fields) and the
 * bundled Java output shape; messages live as nested classes inside the
 * outer `Proto2BundledProto`.
 */
class Proto2BundledTest {
    @JvmField
    @RegisterExtension
    val grpc = InProcessGrpcExtension(
        ::RegisterServerImpl,
        RegisterServiceGrpcKt::RegisterServiceCoroutineStub,
    )

    @Test
    fun `proto2 required and optional fields round trip in bundled mode`() = runTest {
        val response = grpc.stub.register(
            Proto2BundledProto.RegisterRequest.newBuilder()
                .setUsername("alice")
                .setDisplayName("Alice A.")
                .build()
        )
        assertThat(response.userId).isEqualTo("user:alice")
    }

    @Test
    fun `optional field absence is observable`() = runTest {
        val response = grpc.stub.register(
            Proto2BundledProto.RegisterRequest.newBuilder().setUsername("bob").build()
        )
        assertThat(response.userId).isEqualTo("user:bob")
    }

    @Test
    fun `descriptor uses the bundled outer class as schema source`() {
        assertThat(RegisterServiceGrpcKt.serviceDescriptor.name)
            .isEqualTo("proto2_bundled.RegisterService")
    }
}

private class RegisterServerImpl : RegisterServiceGrpcKt.RegisterServiceCoroutineImplBase() {
    override suspend fun register(
        request: Proto2BundledProto.RegisterRequest,
    ): Proto2BundledProto.RegisterResponse =
        Proto2BundledProto.RegisterResponse.newBuilder()
            .setUserId("user:${request.username}")
            .build()
}
