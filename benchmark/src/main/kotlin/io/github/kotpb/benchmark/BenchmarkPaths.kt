package io.github.kotpb.benchmark

import java.nio.file.Path
import java.util.Properties

/**
 * Reads bench.properties off the classpath. The Gradle build emits this file
 * during prepareBenchTools, with absolute paths to every external tool the
 * benchmark needs.
 */
class BenchmarkPaths private constructor(private val props: Properties) {
    fun protoc(): Path = path("protoc")
    fun oursJvm(): Path = path("ours.jvm")
    fun oursNative(): Path = path("ours.native")
    fun upstreamJava(): Path = path("upstream.java")
    fun upstreamKotlin(): Path = path("upstream.kotlin")
    fun hyperfine(): Path = path("hyperfine")
    fun fixturesDir(): Path = path("fixtures.dir")
    fun outRoot(): Path = path("out.root")
    fun reportsDir(): Path = path("reports.dir")

    private fun path(key: String): Path =
        Path.of(props.getProperty(key) ?: error("missing bench.properties key: $key"))

    companion object {
        fun load(): BenchmarkPaths {
            val props = Properties()
            val cl = BenchmarkPaths::class.java.classLoader
            val input = cl.getResourceAsStream("bench.properties")
                ?: error("bench.properties not on classpath — run :benchmark:prepareBenchTools")
            input.use { props.load(it) }
            return BenchmarkPaths(props)
        }
    }
}
