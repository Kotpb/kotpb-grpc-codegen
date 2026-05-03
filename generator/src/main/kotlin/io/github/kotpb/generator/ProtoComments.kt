package io.github.kotpb.generator

import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorProto.SERVICE_FIELD_NUMBER
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto.METHOD_FIELD_NUMBER

/**
 * Lookup of `leading_comments` from a file's `SourceCodeInfo`, keyed by the
 * descriptor `path` that protoc encodes for each element.
 *
 * `[FileDescriptorProto.service, i]` is the i-th service in the file, and
 * `[FileDescriptorProto.service, i, ServiceDescriptorProto.method, j]` is
 * the j-th method of that service.
 */
class ProtoComments private constructor(private val byPath: Map<List<Int>, String>) {
    fun forService(serviceIndex: Int): String? =
        byPath[listOf(SERVICE_FIELD_NUMBER, serviceIndex)]

    fun forMethod(serviceIndex: Int, methodIndex: Int): String? =
        byPath[listOf(SERVICE_FIELD_NUMBER, serviceIndex, METHOD_FIELD_NUMBER, methodIndex)]

    companion object {
        val EMPTY = ProtoComments(emptyMap())

        fun of(file: FileDescriptorProto): ProtoComments {
            if (!file.hasSourceCodeInfo()) return EMPTY
            val map = mutableMapOf<List<Int>, String>()
            for (location in file.sourceCodeInfo.locationList) {
                val leading = location.leadingComments
                if (leading.isEmpty()) continue
                map[location.pathList.toList()] = clean(leading)
            }
            return ProtoComments(map)
        }

        // protoc emits leading_comments with a single space prefix on each line
        // (matching the source). Strip it so KotlinPoet's KDoc renderer doesn't
        // produce double-spaced output.
        private fun clean(raw: String): String =
            raw.lines().joinToString("\n") { it.removePrefix(" ") }.trim()
    }
}
