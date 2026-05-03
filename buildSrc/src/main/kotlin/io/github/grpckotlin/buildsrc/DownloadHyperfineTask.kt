package io.github.grpckotlin.buildsrc

import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream
import javax.inject.Inject

/**
 * Downloads a hyperfine release archive from GitHub Releases, extracts the
 * single executable from it, and copies it to [destExe]. Retries 5× with
 * exponential backoff on transient HTTP failures (notably GitHub's edge layer
 * occasionally serves stuck-cached 502s for download URLs).
 *
 * Lives in `buildSrc` so the body is a real Kotlin class — no script-class
 * capture, fully configuration-cache-friendly.
 */
abstract class DownloadHyperfineTask
@Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {

    @get:Input
    abstract val version: Property<String>

    /** The release-asset filename suffix, e.g. `x86_64-pc-windows-msvc.zip`. */
    @get:Input
    abstract val asset: Property<String>

    @get:OutputFile
    abstract val destExe: RegularFileProperty

    /** Scratch directory for the download + unpacked archive. Cleaned on each run. */
    @get:Internal
    abstract val workDir: DirectoryProperty

    @TaskAction
    fun run() {
        val v = version.get()
        val a = asset.get()
        val dest = destExe.get().asFile
        val work = workDir.get().asFile.apply { mkdirs() }
        val baseUrl = "https://github.com/sharkdp/hyperfine/releases/download/v$v/hyperfine-v$v-$a"
        val archive = work.resolve("_dl-$a")

        downloadWithRetry(baseUrl, archive)
        extract(a, archive, work, dest)
    }

    private fun downloadWithRetry(baseUrl: String, dest: File) {
        var lastError: Exception? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            // Cache-buster only on retries: GitHub's edge layer occasionally
            // stuck-caches 502s for download URLs, and any query string flips
            // the cache key. First attempt stays clean so healthy fetches
            // remain HTTP-cacheable.
            val url = if (attempt == 1) baseUrl else "$baseUrl?_t=${System.currentTimeMillis()}"
            try {
                logger.lifecycle("Downloading hyperfine: $url")
                httpDownload(url, dest)
                return
            } catch (e: Exception) {
                lastError = e
                if (attempt < MAX_ATTEMPTS) {
                    val backoffSec = (1L shl (attempt - 1)).coerceAtMost(MAX_BACKOFF_SEC)
                    logger.lifecycle("  attempt $attempt failed (${e.message}); retrying in ${backoffSec}s...")
                    Thread.sleep(backoffSec * MILLIS_PER_SEC)
                }
            }
        }
        throw lastError ?: error("download failed without recording an exception")
    }

    private fun httpDownload(url: String, dest: File) {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        try {
            val code = conn.responseCode
            // 5xx (e.g. 502 from GitHub's stuck cache) and 4xx must surface as
            // exceptions so the retry loop catches them, not write the HTML
            // error body to disk and fail later in extraction.
            check(code in HTTP_OK_LO..HTTP_OK_HI) { "HTTP $code for $url" }
            conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
    }

    private fun extract(asset: String, archive: File, work: File, dest: File) {
        val unpackDir = work.resolve("_unpack")
        try {
            unpackDir.deleteRecursively()
            unpackDir.mkdirs()
            val tree = if (asset.endsWith(".zip")) {
                archiveOperations.zipTree(archive)
            } else {
                // ArchiveOperations.tarTree only takes a plain tar; gunzip
                // ourselves with the JDK then hand the .tar over.
                val tar = work.resolve("_dl.tar")
                GZIPInputStream(archive.inputStream()).use { input ->
                    tar.outputStream().use { input.copyTo(it) }
                }
                archiveOperations.tarTree(tar)
            }
            fileSystemOperations.copy {
                from(tree)
                into(unpackDir)
            }
            // Archive layout: hyperfine-vX.Y.Z-<triple>/hyperfine[.exe]
            val exeName = dest.name
            val found = unpackDir.walkTopDown().firstOrNull { it.isFile && it.name == exeName }
                ?: error("hyperfine binary not found inside ${archive.name}")
            found.copyTo(dest, overwrite = true)
            if (!exeName.endsWith(".exe")) dest.setExecutable(true, false)
        } finally {
            archive.delete()
            unpackDir.deleteRecursively()
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val MAX_BACKOFF_SEC = 30L
        private const val MILLIS_PER_SEC = 1000L
        private const val HTTP_OK_LO = 200
        private const val HTTP_OK_HI = 299
    }
}
