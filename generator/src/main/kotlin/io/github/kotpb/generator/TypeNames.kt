package io.github.kotpb.generator

import com.squareup.kotlinpoet.ClassName

object TypeNames {
    val Channel = ClassName("io.grpc", "Channel")
    val CallOptions = ClassName("io.grpc", "CallOptions")
    val Metadata = ClassName("io.grpc", "Metadata")
    val Status = ClassName("io.grpc", "Status")
    val StatusException = ClassName("io.grpc", "StatusException")
    val MethodDescriptor = ClassName("io.grpc", "MethodDescriptor")
    val ServiceDescriptor = ClassName("io.grpc", "ServiceDescriptor")
    val ServerServiceDefinition = ClassName("io.grpc", "ServerServiceDefinition")
    val BindableService = ClassName("io.grpc", "BindableService")

    val ProtoUtils = ClassName("io.grpc.protobuf", "ProtoUtils")
    val ProtoLiteUtils = ClassName("io.grpc.protobuf.lite", "ProtoLiteUtils")
    val ProtoFileDescriptorSupplier = ClassName("io.grpc.protobuf", "ProtoFileDescriptorSupplier")
    val ProtoServiceDescriptorSupplier = ClassName("io.grpc.protobuf", "ProtoServiceDescriptorSupplier")
    val ProtoMethodDescriptorSupplier = ClassName("io.grpc.protobuf", "ProtoMethodDescriptorSupplier")

    val Descriptors = ClassName("com.google.protobuf", "Descriptors")
    val FileDescriptor = ClassName("com.google.protobuf.Descriptors", "FileDescriptor")
    val ProtoServiceDescriptor = ClassName("com.google.protobuf.Descriptors", "ServiceDescriptor")
    val ProtoMethodDescriptor = ClassName("com.google.protobuf.Descriptors", "MethodDescriptor")

    val Flow = ClassName("kotlinx.coroutines.flow", "Flow")
    val CoroutineContext = ClassName("kotlin.coroutines", "CoroutineContext")
    val EmptyCoroutineContext = ClassName("kotlin.coroutines", "EmptyCoroutineContext")

    val AbstractCoroutineStub = ClassName("io.grpc.kotlin", "AbstractCoroutineStub")
    val AbstractCoroutineServerImpl = ClassName("io.grpc.kotlin", "AbstractCoroutineServerImpl")
    val ClientCalls = ClassName("io.grpc.kotlin", "ClientCalls")
    val ServerCalls = ClassName("io.grpc.kotlin", "ServerCalls")

    val Generated = ClassName("javax.annotation", "Generated")
}
