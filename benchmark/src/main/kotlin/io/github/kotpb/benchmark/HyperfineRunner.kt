package io.github.kotpb.benchmark

import java.nio.file.Files

/**
 * Builds a single hyperfine invocation that benchmarks every (variant,
 * fixture) pair and writes JSON + markdown reports.
 *
 * Java's role is orchestration only — all timing happens inside hyperfine.
 */
object HyperfineRunner {
    const val WARMUPS = 1
    const val RUNS = 6

    @JvmStatic
    fun main(args: Array<String>) {
        val paths = BenchmarkPaths.load()
        val jsonOut = paths.reportsDir().resolve("results.json")
        val mdOut = paths.reportsDir().resolve("results.md")
        Files.createDirectories(paths.reportsDir())

        val variants = ProtocVariant.selected()
        val fixtures = ProtocCommand.selectedFixtures()

        val hf = mutableListOf(
            paths.hyperfine().toString(),
            "--warmup", WARMUPS.toString(),
            "--runs", RUNS.toString(),
            "--export-json", jsonOut.toString(),
            "--export-markdown", mdOut.toString(),
        )

        for (fixture in fixtures) {
            for (variant in variants) {
                val outDir = paths.outRoot()
                    .resolve("timings").resolve(variant.label).resolve(fixture)
                Files.createDirectories(outDir)
                val cmd = ProtocCommand.build(paths, variant, fixture, outDir)
                hf += listOf("--command-name", commandName(variant, fixture), joinShellQuoted(cmd))
            }
        }

        val cells = variants.size * fixtures.size
        println("Running hyperfine with $cells benchmarks ($WARMUPS warmup + $RUNS runs each)...")
        val rc = ProcessBuilder(hf).inheritIO().start().waitFor()
        check(rc == 0) { "hyperfine failed with exit $rc" }

        println()
        println("Wrote $jsonOut")
        println("Wrote $mdOut")
    }

    fun commandName(variant: ProtocVariant, fixture: String): String =
        "${variant.label} / $fixture"

    /**
     * Join a command into one shell-passable string. Hyperfine receives each
     * command as a single positional argument and runs it via the OS shell;
     * args with spaces or quote-sensitive characters need surrounding quotes.
     * Our paths contain neither special chars nor (typically) spaces, but
     * quoting defensively keeps the bench portable to project paths under
     * names like `My Documents`.
     */
    fun joinShellQuoted(args: List<String>): String = buildString {
        for (arg in args) {
            if (isNotEmpty()) append(' ')
            if (needsQuoting(arg)) {
                append('"').append(arg.replace("\"", "\\\"")).append('"')
            } else {
                append(arg)
            }
        }
    }

    private fun needsQuoting(arg: String): Boolean {
        if (arg.isEmpty()) return true
        return arg.any { it.isWhitespace() || it == '"' || it == '\'' || it == '&' || it == '|' }
    }
}
