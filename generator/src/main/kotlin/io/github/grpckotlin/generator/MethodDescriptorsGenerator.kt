package io.github.grpckotlin.generator

import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

object MethodDescriptorsGenerator {
    private val MethodType = TypeNames.MethodDescriptor.nestedClass("MethodType")

    fun apply(builder: TypeSpec.Builder, ctx: ServiceContext) {
        ctx.service.methodList.forEachIndexed { methodIndex, method ->
            val requestType = ctx.classNameOf(method.inputType)
            val responseType = ctx.classNameOf(method.outputType)
            val kind = MethodKind.of(method)
            val marshaller = if (ctx.config.lite) TypeNames.ProtoLiteUtils else TypeNames.ProtoUtils
            val methodTypeConstant = MemberName(MethodType, kind.name)

            val property = PropertySpec.builder(
                ctx.getMethodPropertyName(method.name),
                TypeNames.MethodDescriptor.parameterizedBy(requestType, responseType),
            ).delegateLazy {
                addStatement("%T.newBuilder<%T, %T>()", TypeNames.MethodDescriptor, requestType, responseType)
                addStatement(".setType(%M)", methodTypeConstant)
                addStatement(
                    ".setFullMethodName(%T.generateFullMethodName(SERVICE_NAME, %S))",
                    TypeNames.MethodDescriptor, method.name,
                )
                addStatement(".setSampledToLocalTracing(true)")
                addStatement(
                    ".setRequestMarshaller(%T.marshaller(%T.getDefaultInstance()))",
                    marshaller, requestType,
                )
                addStatement(
                    ".setResponseMarshaller(%T.marshaller(%T.getDefaultInstance()))",
                    marshaller, responseType,
                )
                addStatement(".setSchemaDescriptor(%T(%S))", ctx.methodDescriptorSupplierClassName, method.name)
                addStatement(".build()")
            }

            ctx.methodComment(methodIndex)?.let { property.addKdoc("%L", it) }
            if (method.options.deprecated) {
                property.addAnnotation(Annotations.deprecated(Annotations.DEPRECATED_METHOD_MESSAGE))
            }
            builder.addProperty(property.build())
        }
    }
}
