package io.github.grpckotlin.generator

import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto
import com.squareup.kotlinpoet.ClassName

class ServiceContext(
    val file: FileDescriptorProto,
    val service: ServiceDescriptorProto,
    val serviceIndex: Int,
    val config: GeneratorConfig,
    private val typeIndex: ProtoTypeIndex,
    private val comments: ProtoComments = ProtoComments.EMPTY,
) {
    val protoPackage: String = file.`package`
    val kotlinPackage: String = DescriptorUtil.resolveKotlinPackage(file, config)
    val serviceName: String = service.name
    val fullyQualifiedServiceName: String = DescriptorUtil.fullyQualifiedServiceName(file, service.name)
    val javaOuterClass: ClassName = ClassName(
        DescriptorUtil.javaPackageOf(file),
        DescriptorUtil.resolveJavaOuterClassName(file),
    )
    val outerObjectName: String = "${service.name}GrpcKt"
    val outerObjectClassName: ClassName = ClassName(kotlinPackage, outerObjectName)

    val fileDescriptorSupplierClassName: ClassName =
        ClassName(kotlinPackage, outerObjectName, "${service.name}FileDescriptorSupplier")
    val methodDescriptorSupplierClassName: ClassName =
        ClassName(kotlinPackage, outerObjectName, "${service.name}MethodDescriptorSupplier")

    val coroutineStubClassName: ClassName =
        ClassName(kotlinPackage, outerObjectName, "${service.name}CoroutineStub")
    val coroutineImplBaseClassName: ClassName =
        ClassName(kotlinPackage, outerObjectName, "${service.name}CoroutineImplBase")

    private val classNameCache = mutableMapOf<String, ClassName>()

    fun classNameOf(protoFqName: String): ClassName =
        classNameCache.getOrPut(protoFqName) {
            DescriptorUtil.classNameForProtoType(protoFqName, file, typeIndex)
        }

    fun getMethodPropertyName(methodName: String): String =
        "get${methodName.replaceFirstChar { it.uppercaseChar() }}Method"

    fun kotlinMethodName(methodName: String): String =
        methodName.replaceFirstChar { it.lowercaseChar() }

    fun serviceComment(): String? =
        if (config.includeComments) comments.forService(serviceIndex) else null

    fun methodComment(methodIndex: Int): String? =
        if (config.includeComments) comments.forMethod(serviceIndex, methodIndex) else null
}
