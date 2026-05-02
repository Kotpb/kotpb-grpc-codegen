package io.github.grpckotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

object ServiceDescriptorGenerator {
    fun apply(builder: TypeSpec.Builder, ctx: ServiceContext) {
        val initializer = CodeBlock.builder()
            .add("lazy {⇥\n")
            .add("%T.newBuilder(SERVICE_NAME)\n", TypeNames.ServiceDescriptor)
            .add(".setSchemaDescriptor(%T)\n", ctx.fileDescriptorSupplierClassName)
        for (method in ctx.service.methodList) {
            initializer.add(".addMethod(%N)\n", ctx.getMethodPropertyName(method.name))
        }
        initializer.add(".build()⇤\n}")

        builder.addProperty(
            PropertySpec.builder("serviceDescriptor", TypeNames.ServiceDescriptor)
                .delegate(initializer.build())
                .build()
        )
    }
}
