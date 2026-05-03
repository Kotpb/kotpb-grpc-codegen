package io.github.kotpb.benchmark

import java.nio.file.Files
import java.nio.file.Path

/**
 * One-shot generator for the small/medium/large fixture .proto files. Run
 * via :benchmark:regenFixtures only when fixture sizes need to change; the
 * three .proto files are committed to the repo so timings don't drift across
 * machines.
 */
object FixtureGenerator {
    private data class Spec(val label: String, val services: Int, val rpcsPerService: Int)
    private data class RpcKind(val prefix: String, val inStream: String, val outStream: String)

    // Inline literals are the spec — naming each dimension as its own constant
    // makes the table harder to read than the table itself, so suppress here.
    @Suppress("MagicNumber")
    private val SPECS = listOf(
        Spec("small", services = 1, rpcsPerService = 5),     // 5 RPCs
        Spec("medium", services = 5, rpcsPerService = 4),    // 20 RPCs
        Spec("large", services = 10, rpcsPerService = 10),   // 100 RPCs
    )

    val LABELS: List<String> = SPECS.map { it.label }

    private val KINDS = listOf(
        RpcKind("Unary", "", ""),
        RpcKind("ServerStream", "", "stream "),
        RpcKind("ClientStream", "stream ", ""),
        RpcKind("BidiStream", "stream ", "stream "),
    )

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "usage: FixtureGenerator <out-dir>" }
        val outDir = Path.of(args[0])
        Files.createDirectories(outDir)
        for (spec in SPECS) {
            val file = outDir.resolve("${spec.label}.proto")
            val content = render(spec)
            Files.writeString(file, content)
            val lines = content.lineSequence().count()
            val rpcs = spec.services * spec.rpcsPerService
            println(
                "${spec.label}: ${spec.services} svc * ${spec.rpcsPerService} rpcs = " +
                    "$rpcs rpcs, ${rpcs * 2} msgs, $lines lines",
            )
        }
    }

    private fun render(spec: Spec): String = buildString {
        append("syntax = \"proto3\";\n")
        append("package ${spec.label};\n")
        append("option java_package = \"com.example.${spec.label}\";\n")
        append("option java_multiple_files = true;\n\n")

        for (s in 0 until spec.services) {
            for (r in 0 until spec.rpcsPerService) {
                for (kind in listOf("Req", "Resp")) {
                    append("message S${s}M${r}$kind {\n")
                    append("  string id = 1;\n")
                    append("  string name = 2;\n")
                    append("  int64 ts = 3;\n")
                    append("  bytes payload = 4;\n")
                    append("  repeated string tags = 5;\n")
                    append("}\n")
                }
            }
            append('\n')
        }

        for (s in 0 until spec.services) {
            append("service Service$s {\n")
            for (r in 0 until spec.rpcsPerService) {
                val kind = KINDS[r % KINDS.size]
                append("  rpc ${kind.prefix}$r(${kind.inStream}S${s}M${r}Req)")
                append(" returns (${kind.outStream}S${s}M${r}Resp);\n")
            }
            append("}\n\n")
        }
    }
}
