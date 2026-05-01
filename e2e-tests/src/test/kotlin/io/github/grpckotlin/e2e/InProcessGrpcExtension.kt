package io.github.grpckotlin.e2e

import io.grpc.BindableService
import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.kotlin.AbstractCoroutineStub
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class InProcessGrpcExtension<S : AbstractCoroutineStub<S>>(
    private val serviceFactory: () -> BindableService,
    private val stubFactory: (Channel) -> S,
) : BeforeEachCallback, AfterEachCallback {
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel
    lateinit var stub: S
        private set

    override fun beforeEach(context: ExtensionContext) {
        val name = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(serviceFactory())
            .build()
            .start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        stub = stubFactory(channel)
    }

    override fun afterEach(context: ExtensionContext) {
        channel.shutdownNow()
        server.shutdownNow()
    }
}
