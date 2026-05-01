package io.github.grpckotlin.e2e

import com.example.calc.CalcRequest
import com.example.calc.CalcResponse
import com.example.calc.CalcServiceGrpcKt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class Editions2023CalcTest {
    @JvmField
    @RegisterExtension
    val grpc = InProcessGrpcExtension(
        ::CalcServerImpl,
        CalcServiceGrpcKt::CalcServiceCoroutineStub,
    )

    @Test
    fun `unary add for edition 2023`() = runTest {
        val response = grpc.stub.add(CalcRequest.newBuilder().setA(2).setB(3).build())
        assertThat(response.result).isEqualTo(5L)
    }

    @Test
    fun `server streaming for edition 2023`() = runTest {
        val responses = grpc.stub.addMany(
            CalcRequest.newBuilder().setA(10).setB(1).build()
        ).toList()
        assertThat(responses.map { it.result }).containsExactly(11L, 12L, 13L)
    }

    @Test
    fun `client streaming for edition 2023`() = runTest {
        val requests = flow {
            emit(CalcRequest.newBuilder().setA(1).setB(2).build())
            emit(CalcRequest.newBuilder().setA(3).setB(4).build())
        }
        val response = grpc.stub.sum(requests)
        assertThat(response.result).isEqualTo(10L)
    }

    @Test
    fun `bidi for edition 2023`() = runTest {
        val requests = flow {
            emit(CalcRequest.newBuilder().setA(1).setB(2).build())
            emit(CalcRequest.newBuilder().setA(10).setB(20).build())
        }
        val responses = grpc.stub.stream(requests).toList()
        assertThat(responses.map { it.result }).containsExactly(3L, 30L)
    }
}

private class CalcServerImpl : CalcServiceGrpcKt.CalcServiceCoroutineImplBase() {
    override suspend fun add(request: CalcRequest): CalcResponse =
        CalcResponse.newBuilder().setResult(request.a + request.b).build()

    override fun addMany(request: CalcRequest): Flow<CalcResponse> = flow {
        for (i in 1..3) {
            emit(CalcResponse.newBuilder().setResult(request.a + request.b * i).build())
        }
    }

    override suspend fun sum(requests: Flow<CalcRequest>): CalcResponse {
        var total = 0L
        requests.collect { total += it.a + it.b }
        return CalcResponse.newBuilder().setResult(total).build()
    }

    override fun stream(requests: Flow<CalcRequest>): Flow<CalcResponse> = flow {
        requests.collect {
            emit(CalcResponse.newBuilder().setResult(it.a + it.b).build())
        }
    }
}
