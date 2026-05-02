package io.github.grpckotlin.e2e

import com.example.nested.NestedProto
import com.example.nested.InvoiceServiceGrpcKt
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Exercises the `java_multiple_files = false` (default) path, where
 * protoc-gen-java emits message classes nested inside the outer file
 * class (`NestedProto.InvoiceRequest`). Our generator's
 * DescriptorUtil.classNameForProtoType has a dedicated branch for this
 * case; this test fails to compile if that branch ever stops emitting
 * the qualifier.
 */
class NestedMessagesTest {
    @JvmField
    @RegisterExtension
    val grpc = InProcessGrpcExtension(
        ::InvoiceServerImpl,
        InvoiceServiceGrpcKt::InvoiceServiceCoroutineStub,
    )

    @Test
    fun `nested-message types round-trip through the generated stub`() = runTest {
        val response = grpc.stub.getInvoice(
            NestedProto.InvoiceRequest.newBuilder().setId("INV-42").build()
        )
        assertThat(response.id).isEqualTo("INV-42")
        assertThat(response.amountCents).isEqualTo(12_345L)
    }

    @Test
    fun `descriptor schema points at the outer class's getDescriptor`() {
        // The supplier emitted by our generator delegates to NestedProto
        // .getDescriptor(); the service descriptor's name still resolves
        // to the canonical proto FQ name.
        assertThat(InvoiceServiceGrpcKt.serviceDescriptor.name)
            .isEqualTo("test.nested.InvoiceService")
    }
}

private class InvoiceServerImpl : InvoiceServiceGrpcKt.InvoiceServiceCoroutineImplBase() {
    override suspend fun getInvoice(
        request: NestedProto.InvoiceRequest,
    ): NestedProto.InvoiceResponse =
        NestedProto.InvoiceResponse.newBuilder()
            .setId(request.id)
            .setAmountCents(12_345L)
            .build()
}
