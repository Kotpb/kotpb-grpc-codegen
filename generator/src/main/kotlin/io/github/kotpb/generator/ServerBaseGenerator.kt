package io.github.kotpb.generator

import com.google.protobuf.DescriptorProtos.MethodDescriptorProto
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec

object ServerBaseGenerator {
    fun apply(builder: TypeSpec.Builder, ctx: ServiceContext) {
        val classBuilder = TypeSpec.classBuilder(ctx.coroutineImplBaseClassName.simpleName)
            .addModifiers(KModifier.ABSTRACT)
            .superclass(TypeNames.AbstractCoroutineServerImpl)
            .addSuperclassConstructorParameter("coroutineContext")
            .addSuperinterface(TypeNames.BindableService)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(
                        ParameterSpec.builder("coroutineContext", TypeNames.CoroutineContext)
                            .defaultValue("%T", TypeNames.EmptyCoroutineContext)
                            .build()
                    )
                    .build()
            )

        ctx.serviceComment()?.let { classBuilder.addKdoc("%L", it) }
        if (ctx.service.options.deprecated) {
            classBuilder.addAnnotation(Annotations.deprecated(Annotations.DEPRECATED_SERVICE_MESSAGE))
        }

        classBuilder.addType(serviceDescriptorCompanion(ctx))

        ctx.service.methodList.forEachIndexed { methodIndex, method ->
            classBuilder.addFunction(buildOpenServerMethod(ctx, method, methodIndex))
        }
        classBuilder.addFunction(buildBindServiceMethod(ctx))

        builder.addType(classBuilder.build())
    }

    private fun buildOpenServerMethod(
        ctx: ServiceContext,
        method: MethodDescriptorProto,
        methodIndex: Int,
    ): FunSpec {
        val kind = MethodKind.of(method)
        val requestType = ctx.classNameOf(method.inputType)
        val responseType = ctx.classNameOf(method.outputType)

        val builder = FunSpec.builder(ctx.kotlinMethodName(method.name))
            .addModifiers(KModifier.OPEN)
            .addParameter(kind.requestParamName, kind.requestType(requestType))
            .returns(kind.responseType(responseType))
        if (!kind.serverStreaming) builder.addModifiers(KModifier.SUSPEND)

        ctx.methodComment(methodIndex)?.let { builder.addKdoc("%L", it) }
        if (method.options.deprecated) {
            builder.addAnnotation(Annotations.deprecated(Annotations.DEPRECATED_METHOD_MESSAGE))
        }

        builder.addStatement(
            "throw %T(%T.UNIMPLEMENTED.withDescription(%S))",
            TypeNames.StatusException,
            TypeNames.Status,
            "Method ${ctx.fullyQualifiedServiceName}.${method.name} is unimplemented",
        )
        return builder.build()
    }

    private fun buildBindServiceMethod(ctx: ServiceContext): FunSpec {
        val body = CodeBlock.builder()
            .addStatement("return %T.builder(serviceDescriptor)", TypeNames.ServerServiceDefinition)
        for (method in ctx.service.methodList) {
            val kind = MethodKind.of(method)
            body.addStatement(
                ".addMethod(%T.%N(context, %N, ::%N))",
                TypeNames.ServerCalls,
                kind.serverMethodDefinitionFn,
                ctx.getMethodPropertyName(method.name),
                ctx.kotlinMethodName(method.name),
            )
        }
        body.addStatement(".build()")

        val builder = FunSpec.builder("bindService")
            .addModifiers(KModifier.FINAL, KModifier.OVERRIDE)
            .returns(TypeNames.ServerServiceDefinition)
            .addCode(body.build())

        // bindService has to reference every method (deprecated or not) and
        // every method descriptor (which is also marked deprecated when its
        // method is). Suppress so this internal plumbing compiles cleanly.
        if (ctx.service.options.deprecated || ctx.service.methodList.any { it.options.deprecated }) {
            builder.addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember("%S", "DEPRECATION")
                    .build()
            )
        }

        return builder.build()
    }
}
