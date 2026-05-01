package io.github.grpckotlin.generator

import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto
import com.squareup.kotlinpoet.ClassName

class ServiceContext(
    val file: FileDescriptorProto,
    val service: ServiceDescriptorProto,
    val config: GeneratorConfig,
    private val typeIndex: ProtoTypeIndex,
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

    val baseDescriptorSupplierClassName: ClassName =
        ClassName(kotlinPackage, outerObjectName, "${service.name}BaseDescriptorSupplier")
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
}
