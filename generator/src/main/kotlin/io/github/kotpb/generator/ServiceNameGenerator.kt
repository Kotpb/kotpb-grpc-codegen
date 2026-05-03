package io.github.kotpb.generator

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

object ServiceNameGenerator {
    fun apply(builder: TypeSpec.Builder, ctx: ServiceContext) {
        builder.addProperty(
            PropertySpec.builder("SERVICE_NAME", String::class)
                .addModifiers(KModifier.CONST)
                .initializer("%S", ctx.fullyQualifiedServiceName)
                .build()
        )
    }
}
