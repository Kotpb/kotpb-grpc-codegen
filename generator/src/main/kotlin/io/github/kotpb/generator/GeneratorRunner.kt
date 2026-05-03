package io.github.kotpb.generator

import com.google.protobuf.DescriptorProtos.Edition
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse

object GeneratorRunner {
    fun runOnStdio() {
        val request = CodeGeneratorRequest.parseFrom(System.`in`)
        val response = run(request)
        response.writeTo(System.out)
        System.out.flush()
    }

    fun run(request: CodeGeneratorRequest): CodeGeneratorResponse {
        val response = CodeGeneratorResponse.newBuilder()
        response.supportedFeatures =
            (CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL_VALUE.toLong()
                or CodeGeneratorResponse.Feature.FEATURE_SUPPORTS_EDITIONS_VALUE.toLong())
        response.minimumEdition = Edition.EDITION_PROTO2.number
        response.maximumEdition = Edition.EDITION_2024.number

        val config = GeneratorConfig.parse(request.parameter)
        val filesToGenerate = request.fileToGenerateList.toSet()
        val protoFileByName = request.protoFileList.associateBy { it.name }
        val typeIndex = ProtoTypeIndex.build(request.protoFileList)

        for (fileName in filesToGenerate) {
            val fileProto = protoFileByName[fileName] ?: continue
            response.addAllFile(ProtoFileCodeGenerator.generate(fileProto, config, typeIndex))
        }
        return response.build()
    }
}
