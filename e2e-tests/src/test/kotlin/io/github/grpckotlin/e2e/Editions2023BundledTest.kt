package io.github.grpckotlin.e2e

import com.example.editions2023_bundled.Editions2023BundledProto
import com.example.editions2023_bundled.LookupServiceGrpcKt
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Matrix cell: edition 2023 with `java_multiple_files = false`. Editions
 * still allow that option (it's removed only in 2024), so this fixture
 * verifies our generator picks the bundled-output branch on the editions
 * line as well as on syntax-based protos.
 */
class Editions2023BundledTest {
    @JvmField
    @RegisterExtension
    val grpc = InProcessGrpcExtension(
        ::LookupServerImpl,
        LookupServiceGrpcKt::LookupServiceCoroutineStub,
    )

    @Test
    fun `lookup round-trips in editions 2023 bundled mode`() = runTest {
        val response = grpc.stub.lookup(
            Editions2023BundledProto.LookupRequest.newBuilder().setKey("answer").build()
        )
        assertThat(response.value).isEqualTo("42")
    }

    @Test
    fun `descriptor name reflects the editions package`() {
        assertThat(LookupServiceGrpcKt.serviceDescriptor.name)
            .isEqualTo("editions2023_bundled.LookupService")
    }
}

private class LookupServerImpl : LookupServiceGrpcKt.LookupServiceCoroutineImplBase() {
    override suspend fun lookup(
        request: Editions2023BundledProto.LookupRequest,
    ): Editions2023BundledProto.LookupResponse =
        Editions2023BundledProto.LookupResponse.newBuilder()
            .setValue(if (request.key == "answer") "42" else "")
            .build()
}
