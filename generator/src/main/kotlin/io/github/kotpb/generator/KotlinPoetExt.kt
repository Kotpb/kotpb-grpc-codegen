package io.github.kotpb.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.PropertySpec

/**
 * Build a `by lazy { ... }` property delegate. The lambda receives a
 * `CodeBlock.Builder` whose contents become the body of the lazy block;
 * KotlinPoet handles the indent push/pop via `beginControlFlow`.
 *
 * Replaces the older pattern that hand-rolled `⇥` / `⇤` indent control
 * characters and did the wrapping at every call site.
 */
internal fun PropertySpec.Builder.delegateLazy(
    body: CodeBlock.Builder.() -> Unit,
): PropertySpec.Builder = delegate(
    CodeBlock.builder()
        .beginControlFlow("lazy")
        .apply(body)
        .endControlFlow()
        .build()
)
