package io.github.grpckotlin.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.asClassName

internal object Annotations {
    fun deprecated(message: String): AnnotationSpec =
        AnnotationSpec.builder(Deprecated::class.asClassName())
            .addMember("%S", message)
            .build()

    fun suppress(vararg names: String): AnnotationSpec {
        val builder = AnnotationSpec.builder(Suppress::class.asClassName())
        for (name in names) builder.addMember("%S", name)
        return builder.build()
    }

    const val DEPRECATED_SERVICE_MESSAGE = "This service is deprecated."
    const val DEPRECATED_METHOD_MESSAGE = "This RPC is deprecated."
}
