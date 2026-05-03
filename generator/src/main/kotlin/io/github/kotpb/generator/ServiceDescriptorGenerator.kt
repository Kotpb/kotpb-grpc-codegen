package io.github.kotpb.generator

import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

object ServiceDescriptorGenerator {
    fun apply(builder: TypeSpec.Builder, ctx: ServiceContext) {
        builder.addProperty(
            PropertySpec.builder("serviceDescriptor", TypeNames.ServiceDescriptor)
                .delegateLazy {
                    addStatement("%T.newBuilder(SERVICE_NAME)", TypeNames.ServiceDescriptor)
                    if (!ctx.config.lite) {
                        addStatement(".setSchemaDescriptor(%T)", ctx.fileDescriptorSupplierClassName)
                    }
                    for (method in ctx.service.methodList) {
                        addStatement(".addMethod(%N)", ctx.getMethodPropertyName(method.name))
                    }
                    addStatement(".build()")
                }
                .build()
        )
    }
}
