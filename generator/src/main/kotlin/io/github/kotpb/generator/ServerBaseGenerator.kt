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
            classBuilder.addFunction(buildServerMethod(ctx, method, methodIndex))
        }
        classBuilder.addFunction(buildBindServiceMethod(ctx))

        builder.addType(classBuilder.build())
    }

    private fun buildServerMethod(
        ctx: ServiceContext,
        method: MethodDescriptorProto,
        methodIndex: Int,
    ): FunSpec {
        val kind = MethodKind.of(method)
        val requestType = ctx.classNameOf(method.inputType)
        val responseType = ctx.classNameOf(method.outputType)

        val builder = FunSpec.builder(ctx.kotlinMethodName(method.name))
            .addModifiers(if (ctx.config.abstractServerMethods) KModifier.ABSTRACT else KModifier.OPEN)
            .addParameter(kind.requestParamName, kind.requestType(requestType))
            .returns(kind.responseType(responseType))
        if (!kind.serverStreaming) builder.addModifiers(KModifier.SUSPEND)

        ctx.methodComment(methodIndex)?.let { builder.addKdoc("%L", it) }
        if (method.options.deprecated) {
            builder.addAnnotation(Annotations.deprecated(Annotations.DEPRECATED_METHOD_MESSAGE))
        }

        if (!ctx.config.abstractServerMethods) {
            builder.addStatement("throw %L", unimplemented(ctx, method, causeVarName = null))
        }
        return builder.build()
    }

    private fun unimplemented(ctx: ServiceContext, method: MethodDescriptorProto, causeVarName: String?): CodeBlock {
        val status = CodeBlock.of(
            "%T.UNIMPLEMENTED.withDescription(%S)",
            TypeNames.Status,
            "Method ${ctx.fullyQualifiedServiceName}.${method.name} is unimplemented",
        )
        return if (causeVarName == null) {
            CodeBlock.of("%T(%L)", TypeNames.StatusException, status)
        } else {
            CodeBlock.of("%T(%L.withCause(%N))", TypeNames.StatusException, status, causeVarName)
        }
    }

    /**
     * The implementation that `bindService` binds [method] to: a call of the
     * service's method, which answers a `NotImplementedError`, as Kotlin's
     * `TODO()` throws, with `UNIMPLEMENTED`, like the default body, rather
     * than the `UNKNOWN` grpc-kotlin-stub answers any other exception with.
     * A method that returns a `Flow` can throw it when called, or as the flow
     * is collected, so both are caught.
     */
    private fun implementation(ctx: ServiceContext, method: MethodDescriptorProto): CodeBlock {
        val kind = MethodKind.of(method)
        val param = kind.requestParamName
        val call = CodeBlock.builder()
            .add("try {\n⇥%N(%N)\n⇤}", ctx.kotlinMethodName(method.name), param)
            .add(" catch (e: %T) {\n⇥throw %L\n⇤}", TypeNames.NotImplementedError, unimplemented(ctx, method, "e"))
            .build()
        val body = if (kind.serverStreaming) {
            CodeBlock.builder()
                .add("(%L).%M { e ->\n⇥", call, TypeNames.FlowCatch)
                .add(
                    "throw if (e is %T) %L else e\n⇤}",
                    TypeNames.NotImplementedError,
                    unimplemented(ctx, method, "e"),
                )
                .build()
        } else {
            call
        }
        return CodeBlock.of("{ %N ->\n⇥%L\n⇤}", param, body)
    }

    private fun buildBindServiceMethod(ctx: ServiceContext): FunSpec {
        val body = CodeBlock.builder()
            .addStatement("return %T.builder(serviceDescriptor)", TypeNames.ServerServiceDefinition)
        for (method in ctx.service.methodList) {
            val kind = MethodKind.of(method)
            body.add(
                ".addMethod(%T.%N(context, %N) %L)\n",
                TypeNames.ServerCalls,
                kind.serverMethodDefinitionFn,
                ctx.getMethodPropertyName(method.name),
                implementation(ctx, method),
            )
        }
        body.addStatement(".build()")

        val builder = FunSpec.builder("bindService")
            .addModifiers(KModifier.FINAL, KModifier.OVERRIDE)
            .returns(TypeNames.ServerServiceDefinition)
            .addCode(body.build())

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
