package io.github.grpckotlin.generator

import com.google.protobuf.DescriptorProtos.Edition
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.squareup.kotlinpoet.ClassName

object DescriptorUtil {
    fun resolveKotlinPackage(file: FileDescriptorProto, config: GeneratorConfig): String {
        config.javaPackageOverride?.takeIf { it.isNotBlank() }?.let { return it }
        return javaPackageOf(file)
    }

    /**
     * The package where protoc-gen-java emits this file's classes. Never affected by our
     * `java_package` plugin option (which only relocates *our* Kotlin output) — message
     * `ClassName`s and the file descriptor accessor must always point at protoc-gen-java's
     * actual output location.
     */
    fun javaPackageOf(file: FileDescriptorProto): String {
        val opts = file.options
        if (opts.hasJavaPackage() && opts.javaPackage.isNotBlank()) return opts.javaPackage
        return file.`package`
    }

    fun resolveJavaOuterClassName(file: FileDescriptorProto): String {
        val opts = file.options
        if (opts.hasJavaOuterClassname() && opts.javaOuterClassname.isNotBlank()) {
            return opts.javaOuterClassname
        }
        val base = file.name.substringAfterLast('/').substringBeforeLast('.')
        val camel = toCamelCase(base)
        if (hasMessageOrEnumOrServiceNamed(file, camel)) return "${camel}OuterClass"
        return camel
    }

    fun outerKotlinClassNameForGrpcKt(file: FileDescriptorProto): String =
        "${resolveJavaOuterClassName(file)}GrpcKt"

    fun isJavaMultipleFiles(file: FileDescriptorProto): Boolean {
        val opts = file.options
        if (opts.hasJavaMultipleFiles()) return opts.javaMultipleFiles
        // Editions 2024+ remove java_multiple_files and default to multi-files behavior.
        if (file.syntax == "editions" && file.edition.number >= Edition.EDITION_2024.number) {
            return true
        }
        return false
    }

    fun classNameForProtoType(
        protoFqName: String,
        currentFile: FileDescriptorProto,
        index: ProtoTypeIndex,
    ): ClassName {
        val sanitized = protoFqName.removePrefix(".")
        val owningFile = index.fileFor(sanitized) ?: currentFile
        val owningJavaPkg = javaPackageOf(owningFile)
        val owningProtoPkg = owningFile.`package`
        val withinPkg = if (owningProtoPkg.isNotEmpty()) sanitized.removePrefix("$owningProtoPkg.") else sanitized
        val parts = withinPkg.split('.')
        return if (isJavaMultipleFiles(owningFile)) {
            ClassName(owningJavaPkg, parts.first(), *parts.drop(1).toTypedArray())
        } else {
            val outer = resolveJavaOuterClassName(owningFile)
            ClassName(owningJavaPkg, outer, *parts.toTypedArray())
        }
    }

    fun fullyQualifiedServiceName(file: FileDescriptorProto, serviceName: String): String =
        if (file.`package`.isEmpty()) serviceName else "${file.`package`}.$serviceName"

    private fun hasMessageOrEnumOrServiceNamed(file: FileDescriptorProto, name: String): Boolean =
        file.messageTypeList.any { it.name == name } ||
            file.enumTypeList.any { it.name == name } ||
            file.serviceList.any { it.name == name }

    private fun toCamelCase(input: String): String =
        input.split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
}
