package io.github.kotpb.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

object DescriptorSuppliersGenerator {
    fun apply(builder: TypeSpec.Builder, ctx: ServiceContext) {
        builder.addType(buildFileSupplier(ctx))
        builder.addType(buildMethodSupplier(ctx))
    }

    /**
     * The file/service supplier is stateless, so it's emitted as an `object`
     * (singleton): zero allocations per service descriptor and one fewer
     * declaration than the upstream three-class pattern.
     */
    private fun buildFileSupplier(ctx: ServiceContext): TypeSpec =
        TypeSpec.objectBuilder(ctx.fileDescriptorSupplierClassName.simpleName)
            .addModifiers(KModifier.PRIVATE)
            .addSuperinterface(TypeNames.ProtoServiceDescriptorSupplier)
            .addFunction(
                FunSpec.builder("getFileDescriptor")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(TypeNames.FileDescriptor)
                    .addStatement("return %T.getDescriptor()", ctx.javaOuterClass)
                    .build()
            )
            .addFunction(
                FunSpec.builder("getServiceDescriptor")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(TypeNames.ProtoServiceDescriptor)
                    .addStatement("return getFileDescriptor().findServiceByName(%S)", ctx.serviceName)
                    .build()
            )
            .build()

    /**
     * The per-method supplier holds the method name as state, so it stays a
     * class. It implements `ProtoMethodDescriptorSupplier` directly and
     * delegates `ProtoServiceDescriptorSupplier` (which transitively covers
     * `ProtoFileDescriptorSupplier`) to the singleton above — no inheritance,
     * no duplication.
     */
    private fun buildMethodSupplier(ctx: ServiceContext): TypeSpec =
        TypeSpec.classBuilder(ctx.methodDescriptorSupplierClassName.simpleName)
            .addModifiers(KModifier.PRIVATE)
            .addSuperinterface(TypeNames.ProtoMethodDescriptorSupplier)
            .addSuperinterface(
                TypeNames.ProtoServiceDescriptorSupplier,
                CodeBlock.of("%T", ctx.fileDescriptorSupplierClassName),
            )
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("methodName", String::class)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("methodName", String::class)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("methodName")
                    .build()
            )
            .addFunction(
                FunSpec.builder("getMethodDescriptor")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(TypeNames.ProtoMethodDescriptor)
                    .addStatement("return getServiceDescriptor().findMethodByName(methodName)")
                    .build()
            )
            .build()
}
