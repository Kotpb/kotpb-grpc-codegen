package io.github.grpckotlin.generator

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

object DescriptorSuppliersGenerator {
    fun apply(builder: TypeSpec.Builder, ctx: ServiceContext) {
        builder.addType(buildBaseSupplier(ctx))
        builder.addType(buildFileSupplier(ctx))
        builder.addType(buildMethodSupplier(ctx))
    }

    private fun buildBaseSupplier(ctx: ServiceContext): TypeSpec {
        val name = ctx.baseDescriptorSupplierClassName.simpleName
        return TypeSpec.classBuilder(name)
            .addModifiers(KModifier.PRIVATE, KModifier.ABSTRACT)
            .addSuperinterface(TypeNames.ProtoFileDescriptorSupplier)
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
    }

    private fun buildFileSupplier(ctx: ServiceContext): TypeSpec {
        val name = ctx.fileDescriptorSupplierClassName.simpleName
        return TypeSpec.classBuilder(name)
            .addModifiers(KModifier.PRIVATE)
            .superclass(ctx.baseDescriptorSupplierClassName)
            .build()
    }

    private fun buildMethodSupplier(ctx: ServiceContext): TypeSpec {
        val name = ctx.methodDescriptorSupplierClassName.simpleName
        return TypeSpec.classBuilder(name)
            .addModifiers(KModifier.PRIVATE)
            .superclass(ctx.baseDescriptorSupplierClassName)
            .addSuperinterface(TypeNames.ProtoMethodDescriptorSupplier)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(ParameterSpec.builder("methodName", String::class).build())
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
}
