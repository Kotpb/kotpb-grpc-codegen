package io.github.grpckotlin.generator.tests

import io.github.grpckotlin.generator.GeneratorRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Verifies that proto leading-comments survive into the generated source as
 * proper, well-formed KDoc — multi-line stays multi-line, blank lines become
 * paragraph breaks, and KDoc-flavored content (backticks, [refs], @tags)
 * passes through untouched.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KDocFidelityTest {
    private val content: String by lazy {
        val req = TestFixtures.requestWithRichComments().toBuilder()
            .setParameter("comments=true")
            .build()
        GeneratorRunner.run(req).getFile(0).content
    }

    @Test
    fun `multi-line proto comment renders as multi-line KDoc block`() {
        // KotlinPoet renders KDoc as `/**` ... `*/` with each interior line
        // prefixed by ` * `. A multi-line comment should produce at least three
        // such interior lines; if the cleaning logic accidentally collapsed
        // newlines, the block would be a single ` * ` line.
        val firstBlock = Regex("""/\*\*\n(?: \*[^\n]*\n)+ \*/""").find(content)
        assertThat(firstBlock).isNotNull
        val interiorLines = firstBlock!!.value.lines().count { it.startsWith(" * ") || it == " *" }
        assertThat(interiorLines).isGreaterThanOrEqualTo(3)
    }

    @Test
    fun `blank lines in proto comment become paragraph breaks in KDoc`() {
        // The fixture has a blank line between the first sentence and the
        // "It exists to demonstrate..." paragraph. KotlinPoet renders blank
        // KDoc lines as ` *` (asterisk, no content) — that is the marker we
        // expect to appear inside the block.
        val serviceBlock = Regex(
            """/\*\*\n(?: \*[^\n]*\n)*? \* EchoService is the canonical fixture[\s\S]*? \*/"""
        ).find(content)
        assertThat(serviceBlock).isNotNull
        // A bare ` *` line (no trailing content) is the paragraph separator.
        assertThat(serviceBlock!!.value).containsPattern("""\n \*\n""")
    }

    @Test
    fun `inline backtick code spans pass through unchanged`() {
        assertThat(content).contains("inline `code` spans")
    }

    @Test
    fun `KDoc-style square-bracket references pass through unchanged`() {
        assertThat(content).contains("[EchoRequest] and [EchoResponse]")
    }

    @Test
    fun `KDoc tags like at-param and at-return pass through unchanged`() {
        assertThat(content).contains("@param request the message to echo")
        assertThat(content).contains("@return the echoed message wrapped in an EchoResponse")
    }

    @Test
    fun `bullet list items keep their leading dash`() {
        assertThat(content).contains("- multi-line text preserved verbatim")
        assertThat(content).contains("- paragraph breaks via blank lines")
    }

    @Test
    fun `every KDoc block opens and closes cleanly`() {
        // No stray `/**` without a matching `*/`, so the rendered file is
        // syntactically well-formed regardless of comment content.
        val openCount = Regex("""/\*\*""").findAll(content).count()
        val closeCount = Regex("""\*/""").findAll(content).count()
        assertThat(openCount).isEqualTo(closeCount)
    }
}
