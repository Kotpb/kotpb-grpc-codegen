package io.github.grpckotlin.benchmark

import java.nio.file.Path

/**
 * Builds the canonical protoc command line for one (variant, fixture, outDir)
 * triple — the same command we hand to hyperfine for timing and to
 * ProcessBuilder for the line-count one-shot run.
 *
 * Every variant emits Java messages via --java_out; only the variant-specific
 * `--plugin=` / `--*_out=` flags differ.
 */
object ProtocCommand {
    val FIXTURES: List<String> = FixtureGenerator.LABELS

    fun build(p: BenchmarkPaths, variant: ProtocVariant, fixture: String, outDir: Path): List<String> {
        val cmd = mutableListOf<String>()
        cmd += p.protoc().toString()
        cmd += "--java_out=$outDir"
        variant.contribute(cmd, p, outDir)
        cmd += "-I"
        cmd += p.fixturesDir().toString()
        cmd += "$fixture.proto"
        return cmd
    }

    /**
     * Filter parsed from -Pfixtures=small,medium. Empty / unset → all fixtures.
     */
    fun selectedFixtures(): List<String> {
        val filter = System.getProperty("fixtures")
        if (filter.isNullOrBlank()) return FIXTURES
        val requested = filter.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (f in requested) {
            require(f in FIXTURES) { "Unknown fixture '$f'. Valid: $FIXTURES" }
        }
        return requested
    }
}
