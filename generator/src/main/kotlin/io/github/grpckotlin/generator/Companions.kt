package io.github.grpckotlin.generator

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

/**
 * Companion object exposing `serviceDescriptor` from the stub or impl-base
 * class itself, so callers with only the class type (or a Java caller via
 * `@JvmStatic`) can read service options without referencing the outer
 * `<Service>GrpcKt` object. Identical between client and server, so it
 * lives here.
 */
internal fun serviceDescriptorCompanion(ctx: ServiceContext): TypeSpec =
    TypeSpec.companionObjectBuilder()
        .addProperty(
            PropertySpec.builder("serviceDescriptor", TypeNames.ServiceDescriptor)
                .addAnnotation(JvmStatic::class)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return %T.serviceDescriptor", ctx.outerObjectClassName)
                        .build()
                )
                .build()
        )
        .build()
