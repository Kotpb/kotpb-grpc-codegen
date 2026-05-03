package io.github.kotpb.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.asClassName

internal object Annotations {
    fun deprecated(message: String): AnnotationSpec =
        AnnotationSpec.builder(Deprecated::class.asClassName())
            .addMember("%S", message)
            .build()

    const val DEPRECATED_SERVICE_MESSAGE = "This service is deprecated."
    const val DEPRECATED_METHOD_MESSAGE = "This RPC is deprecated."
}
