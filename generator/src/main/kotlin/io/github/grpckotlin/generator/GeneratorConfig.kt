package io.github.grpckotlin.generator

data class GeneratorConfig(
    val lite: Boolean = false,
    val javaPackageOverride: String? = null,
) {
    companion object {
        fun parse(parameter: String): GeneratorConfig {
            if (parameter.isBlank()) return GeneratorConfig()
            var lite = false
            var pkg: String? = null
            for (entry in parameter.split(',')) {
                if (entry.isBlank()) continue
                val parts = entry.split('=', limit = 2)
                val key = parts[0].trim()
                val value = parts.getOrNull(1)?.trim()
                when (key) {
                    "lite" -> lite = value == null || value.equals("true", ignoreCase = true)
                    "java_package" -> pkg = value
                }
            }
            return GeneratorConfig(lite = lite, javaPackageOverride = pkg)
        }
    }
}
