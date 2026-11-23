package io.github.kotpb.generator

data class GeneratorConfig(
    val lite: Boolean = false,
    val javaPackageOverride: String? = null,
    val includeComments: Boolean = false,
    val abstractServerMethods: Boolean = false,
) {
    companion object {
        fun parse(parameter: String): GeneratorConfig {
            if (parameter.isBlank()) return GeneratorConfig()
            var lite = false
            var pkg: String? = null
            var comments = false
            var abstractServerMethods = false
            for (entry in parameter.split(',')) {
                if (entry.isBlank()) continue
                val parts = entry.split('=', limit = 2)
                val key = parts[0].trim()
                val value = parts.getOrNull(1)?.trim()
                when (key) {
                    "lite" -> lite = parseBooleanFlag(value)
                    "comments" -> comments = parseBooleanFlag(value)
                    "abstract_server_methods" -> abstractServerMethods = parseBooleanFlag(value)
                    "java_package" -> pkg = value
                }
            }
            return GeneratorConfig(
                lite = lite,
                javaPackageOverride = pkg,
                includeComments = comments,
                abstractServerMethods = abstractServerMethods,
            )
        }

        private fun parseBooleanFlag(value: String?): Boolean =
            value == null || value.equals("true", ignoreCase = true)
    }
}
