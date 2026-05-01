package io.github.grpckotlin.generator

import com.google.protobuf.DescriptorProtos.FileDescriptorProto

/**
 * Maps a proto-FQ message/enum name (e.g. `pkg.Outer` or `pkg.Outer.Inner`) to the file
 * that defines it. Built once per `CodeGeneratorRequest` so cross-file `ClassName`
 * resolution is O(1) instead of scanning every file for every type lookup.
 */
class ProtoTypeIndex private constructor(
    private val fileByTypeName: Map<String, FileDescriptorProto>,
) {
    fun fileFor(protoFqName: String): FileDescriptorProto? {
        var name = protoFqName
        while (true) {
            fileByTypeName[name]?.let { return it }
            val dot = name.lastIndexOf('.')
            if (dot < 0) return null
            name = name.substring(0, dot)
        }
    }

    companion object {
        fun build(files: Iterable<FileDescriptorProto>): ProtoTypeIndex {
            val map = mutableMapOf<String, FileDescriptorProto>()
            for (file in files) {
                val pkg = file.`package`
                val prefix = if (pkg.isEmpty()) "" else "$pkg."
                for (m in file.messageTypeList) indexMessage(map, prefix + m.name, file, m)
                for (e in file.enumTypeList) map[prefix + e.name] = file
            }
            return ProtoTypeIndex(map)
        }

        private fun indexMessage(
            map: MutableMap<String, FileDescriptorProto>,
            fqName: String,
            file: FileDescriptorProto,
            message: com.google.protobuf.DescriptorProtos.DescriptorProto,
        ) {
            map[fqName] = file
            for (nested in message.nestedTypeList) indexMessage(map, "$fqName.${nested.name}", file, nested)
            for (e in message.enumTypeList) map["$fqName.${e.name}"] = file
        }
    }
}
