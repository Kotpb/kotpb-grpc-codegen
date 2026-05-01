package io.github.grpckotlin.generator

import com.google.protobuf.DescriptorProtos.MethodDescriptorProto
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName

enum class MethodKind {
    UNARY,
    CLIENT_STREAMING,
    SERVER_STREAMING,
    BIDI_STREAMING,
    ;

    val clientStreaming: Boolean get() = this == CLIENT_STREAMING || this == BIDI_STREAMING
    val serverStreaming: Boolean get() = this == SERVER_STREAMING || this == BIDI_STREAMING

    val requestParamName: String get() = if (clientStreaming) "requests" else "request"

    val clientCallFn: String get() = when (this) {
        UNARY -> "unaryRpc"
        SERVER_STREAMING -> "serverStreamingRpc"
        CLIENT_STREAMING -> "clientStreamingRpc"
        BIDI_STREAMING -> "bidiStreamingRpc"
    }

    val serverMethodDefinitionFn: String get() = when (this) {
        UNARY -> "unaryServerMethodDefinition"
        SERVER_STREAMING -> "serverStreamingServerMethodDefinition"
        CLIENT_STREAMING -> "clientStreamingServerMethodDefinition"
        BIDI_STREAMING -> "bidiStreamingServerMethodDefinition"
    }

    fun requestType(messageType: ClassName): TypeName =
        if (clientStreaming) TypeNames.Flow.parameterizedBy(messageType) else messageType

    fun responseType(messageType: ClassName): TypeName =
        if (serverStreaming) TypeNames.Flow.parameterizedBy(messageType) else messageType

    companion object {
        fun of(method: MethodDescriptorProto): MethodKind = when {
            method.clientStreaming && method.serverStreaming -> BIDI_STREAMING
            method.clientStreaming -> CLIENT_STREAMING
            method.serverStreaming -> SERVER_STREAMING
            else -> UNARY
        }
    }
}
