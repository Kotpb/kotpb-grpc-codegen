package io.github.grpckotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

object MethodDescriptorsGenerator {
    fun apply(builder: TypeSpec.Builder, ctx: ServiceContext) {
        ctx.service.methodList.forEachIndexed { methodIndex, method ->
            val requestType = ctx.classNameOf(method.inputType)
            val responseType = ctx.classNameOf(method.outputType)
            val kind = MethodKind.of(method)
            val marshallerClass = if (ctx.config.lite) TypeNames.ProtoLiteUtils else TypeNames.ProtoUtils

            val initializer = CodeBlock.builder()
                .add("lazy {⇥\n")
                .add("%T.newBuilder<%T, %T>()\n", TypeNames.MethodDescriptor, requestType, responseType)
                .add(".setType(%T.MethodType.%N)\n", TypeNames.MethodDescriptor, kind.name)
                .add(
                    ".setFullMethodName(%T.generateFullMethodName(SERVICE_NAME, %S))\n",
                    TypeNames.MethodDescriptor, method.name,
                )
                .add(".setSampledToLocalTracing(true)\n")
                .add(
                    ".setRequestMarshaller(%T.marshaller(%T.getDefaultInstance()))\n",
                    marshallerClass, requestType,
                )
                .add(
                    ".setResponseMarshaller(%T.marshaller(%T.getDefaultInstance()))\n",
                    marshallerClass, responseType,
                )
                .add(
                    ".setSchemaDescriptor(%T(%S))\n",
                    ctx.methodDescriptorSupplierClassName, method.name,
                )
                .add(".build()⇤\n}")
                .build()

            val property = PropertySpec.builder(
                ctx.getMethodPropertyName(method.name),
                TypeNames.MethodDescriptor.parameterizedBy(requestType, responseType),
            ).delegate(initializer)
            ctx.methodComment(methodIndex)?.let { property.addKdoc("%L", it) }
            builder.addProperty(property.build())
        }
    }
}
