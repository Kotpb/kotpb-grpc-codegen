package io.github.kotpb.benchmark

import java.nio.file.Path

/**
 * The three protoc plugin pipelines we benchmark. Each variant knows how to
 * append its own `--plugin=` and `--*_out=` flags to a base protoc command.
 */
enum class ProtocVariant(val label: String) {
    OURS_NATIVE("ours-native") {
        override fun contribute(cmd: MutableList<String>, p: BenchmarkPaths, out: Path) {
            cmd += "--plugin=protoc-gen-grpc-kotlin=${p.oursNative()}"
            cmd += "--grpc-kotlin_out=$out"
        }
    },
    OURS_JVM("ours-jvm") {
        override fun contribute(cmd: MutableList<String>, p: BenchmarkPaths, out: Path) {
            cmd += "--plugin=protoc-gen-grpc-kotlin=${p.oursJvm()}"
            cmd += "--grpc-kotlin_out=$out"
        }
    },
    UPSTREAM("upstream") {
        override fun contribute(cmd: MutableList<String>, p: BenchmarkPaths, out: Path) {
            cmd += "--plugin=protoc-gen-grpc-java=${p.upstreamJava()}"
            cmd += "--grpc-java_out=$out"
            cmd += "--plugin=protoc-gen-grpc-kotlin=${p.upstreamKotlin()}"
            cmd += "--grpc-kotlin_out=$out"
        }
    };

    abstract fun contribute(cmd: MutableList<String>, p: BenchmarkPaths, out: Path)

    companion object {
        /**
         * Filter parsed from -Pvariants=OURS_JVM,UPSTREAM. Names match the enum
         * (case-insensitive). Empty / unset → all variants.
         */
        fun selected(): List<ProtocVariant> {
            val filter = System.getProperty("variants")
            if (filter.isNullOrBlank()) return entries
            return filter.split(",")
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .map { valueOf(it) }
        }
    }
}
