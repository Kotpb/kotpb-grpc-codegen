package io.github.grpckotlin.e2e

import com.example.method_options.MethodOptionsProto
import com.example.method_options.OptServiceGrpcKt
import io.grpc.protobuf.ProtoMethodDescriptorSupplier
import io.grpc.protobuf.ProtoServiceDescriptorSupplier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Matrix cell: built-in `option idempotency_level` and custom service/method
 * options.
 *
 * Two surfaces under test:
 *
 * 1. The runtime [io.grpc.MethodDescriptor] flags (`isSafe`, `isIdempotent`).
 *    These are stored on the descriptor itself (not on the proto schema) and
 *    require the codegen to emit the matching builder call. protoc-gen-grpc-
 *    java does this for `idempotency_level = NO_SIDE_EFFECTS / IDEMPOTENT`;
 *    we do too.
 *
 * 2. Custom options reachable through the schema descriptor. The schema
 *    descriptor wires through to the protobuf-java FileDescriptor singleton
 *    that protoc-gen-java emits, which carries every option (built-in and
 *    extension) verbatim from the .proto. So getting at custom options
 *    is just a `getExtension(...)` call on the proto MethodOptions /
 *    ServiceOptions reachable via the supplier.
 */
class MethodOptionsTest {
    private val pure = OptServiceGrpcKt.getPureLookupMethod
    private val idempotent = OptServiceGrpcKt.getIdempotentWriteMethod
    private val plain = OptServiceGrpcKt.getPlainWriteMethod

    // Note: gRPC defines `isIdempotent()` as `idempotent || safe` — safe is
    // strictly stronger (no side effects ⊆ idempotent), so a method marked
    // NO_SIDE_EFFECTS reports BOTH flags true.

    @Test
    fun `NO_SIDE_EFFECTS surfaces as MethodDescriptor isSafe`() {
        assertThat(pure.isSafe).isTrue()
        assertThat(pure.isIdempotent).isTrue()
    }

    @Test
    fun `IDEMPOTENT surfaces as MethodDescriptor isIdempotent but not isSafe`() {
        assertThat(idempotent.isIdempotent).isTrue()
        assertThat(idempotent.isSafe).isFalse()
    }

    @Test
    fun `unset idempotency leaves both flags false`() {
        assertThat(plain.isSafe).isFalse()
        assertThat(plain.isIdempotent).isFalse()
    }

    @Test
    fun `custom method option round-trips through the schema descriptor`() {
        assertThat(methodOption(pure)).isEqualTo("pure-tag")
        assertThat(methodOption(idempotent)).isEqualTo("idempotent-tag")
        // PlainWrite has no `method_label` set, so reading the extension
        // returns the default (empty string).
        assertThat(methodOption(plain)).isEmpty()
    }

    @Test
    fun `custom service option round-trips through the schema descriptor`() {
        val supplier = OptServiceGrpcKt.serviceDescriptor.schemaDescriptor as ProtoServiceDescriptorSupplier
        val serviceOptions = supplier.serviceDescriptor.options
        val label = serviceOptions.getExtension(MethodOptionsProto.serviceLabel)
        assertThat(label).isEqualTo("svc-tag")
    }

    private fun methodOption(descriptor: io.grpc.MethodDescriptor<*, *>): String {
        val supplier = descriptor.schemaDescriptor as ProtoMethodDescriptorSupplier
        return supplier.methodDescriptor.options.getExtension(MethodOptionsProto.methodLabel)
    }
}
