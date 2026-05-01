package io.github.grpckotlin.generator

import com.google.protobuf.DescriptorProtos.MethodDescriptorProto
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec

object ClientStubGenerator {
    fun apply(builder: TypeSpec.Builder, ctx: ServiceContext) {
        val stubClass = ctx.coroutineStubClassName

        val classBuilder = TypeSpec.classBuilder(stubClass.simpleName)
            .superclass(TypeNames.AbstractCoroutineStub.parameterizedBy(stubClass))
            .addSuperclassConstructorParameter("channel")
            .addSuperclassConstructorParameter("callOptions")
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("channel", TypeNames.Channel)
                    .addParameter(
                        ParameterSpec.builder("callOptions", TypeNames.CallOptions)
                            .defaultValue("%T.DEFAULT", TypeNames.CallOptions)
                            .build()
                    )
                    .build()
            )
            .addFunction(
                FunSpec.builder("build")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("channel", TypeNames.Channel)
                    .addParameter("callOptions", TypeNames.CallOptions)
                    .returns(stubClass)
                    .addStatement("return %T(channel, callOptions)", stubClass)
                    .build()
            )

        for (method in ctx.service.methodList) {
            classBuilder.addFunction(buildClientMethod(ctx, method))
        }

        builder.addType(classBuilder.build())
    }

    private fun buildClientMethod(ctx: ServiceContext, method: MethodDescriptorProto): FunSpec {
        val kind = MethodKind.of(method)
        val requestType = ctx.classNameOf(method.inputType)
        val responseType = ctx.classNameOf(method.outputType)

        val funBuilder = FunSpec.builder(ctx.kotlinMethodName(method.name))
            .addParameter(kind.requestParamName, kind.requestType(requestType))
            .addParameter(
                ParameterSpec.builder("headers", TypeNames.Metadata)
                    .defaultValue("%T()", TypeNames.Metadata)
                    .build()
            )
            .returns(kind.responseType(responseType))
        if (!kind.serverStreaming) funBuilder.addModifiers(KModifier.SUSPEND)

        funBuilder.addStatement(
            "return %T.%N(channel, %N, %N, callOptions, headers)",
            TypeNames.ClientCalls,
            kind.clientCallFn,
            ctx.getMethodPropertyName(method.name),
            kind.requestParamName,
        )
        return funBuilder.build()
    }
}
