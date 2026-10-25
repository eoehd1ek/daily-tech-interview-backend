package com.eoehd1ek.tech.llmcalllog.application

import com.eoehd1ek.tech.llmcalllog.application.model.CompleteLlmCallLog
import com.eoehd1ek.tech.llmcalllog.application.model.StartLlmCallLog
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallFailureStage
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallLog
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallPurpose
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallStatus
import com.eoehd1ek.tech.llmcalllog.infrastructure.persistence.LlmCallLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class LlmCallLogWriterTest {
    @Mock
    private lateinit var repository: LlmCallLogRepository

    @InjectMocks
    private lateinit var writer: LlmCallLogWriter

    @Test
    fun `시작 저장은 요청 원문과 STARTED 상태를 저장하고 생성 ID를 반환한다`() {
        // given
        val command = StartLlmCallLog(
            LlmCallPurpose.ANSWER_EVALUATION, 7L, "gateway", "model",
            Instant.parse("2026-10-25T10:00:00Z"), " [ {\"content\":\" input \\n\"} ] ", "{\"x\":true}"
        )
        given(repository.save(any(LlmCallLog::class.java))).willAnswer { invocation ->
            invocation.getArgument<LlmCallLog>(0).also {
                ReflectionTestUtils.setField(it, "id", 12L)
                ReflectionTestUtils.setField(it, "createdAt", command.startedAt.plusMillis(3))
            }
        }

        // when
        val id = writer.start(command)

        // then
        assertThat(id).isEqualTo(12L)
        val captor = ArgumentCaptor.forClass(LlmCallLog::class.java)
        verify(repository).save(captor.capture())
        val saved = captor.value
        assertThat(saved.purpose).isEqualTo(command.purpose)
        assertThat(saved.questionId).isEqualTo(command.questionId)
        assertThat(saved.provider).isEqualTo(command.provider)
        assertThat(saved.requestedModel).isEqualTo(command.requestedModel)
        assertThat(saved.startedAt).isEqualTo(command.startedAt)
        assertThat(saved.requestMessages).isEqualTo(command.requestMessages)
        assertThat(saved.requestOptions).isEqualTo(command.requestOptions)
        assertThat(saved.status).isEqualTo(LlmCallStatus.STARTED)
        assertThat(saved.completedAt).isNull()
        assertThat(saved.durationMs).isNull()
        assertThat(saved.responseData).isNull()
        assertThat(saved.errorData).isNull()
        assertThat(saved.evaluationAttemptId).isNull()
    }

    @Test
    fun `완료 갱신은 응답 메타데이터와 토큰 및 실패 원문을 같은 행에 기록한다`() {
        // given
        val log = log()
        given(repository.findById(12L)).willReturn(Optional.of(log))
        val command = CompleteLlmCallLog(
            LlmCallStatus.FAILED, log.startedAt.plusSeconds(2), 1900,
            "response", "actual-model", "stop", "{\"text\":\" invalid json \"}",
            100, 30, 130, 20, 10, LlmCallFailureStage.RESPONSE_PARSE,
            "{\"type\":\"InvalidResponse\",\"message\":null,\"causes\":[]}"
        )

        // when
        writer.complete(12L, command)

        // then
        assertThat(log.status).isEqualTo(command.status)
        assertThat(log.completedAt).isEqualTo(command.completedAt)
        assertThat(log.durationMs).isEqualTo(command.durationMs)
        assertThat(log.responseId).isEqualTo(command.responseId)
        assertThat(log.responseModel).isEqualTo(command.responseModel)
        assertThat(log.finishReason).isEqualTo(command.finishReason)
        assertThat(log.responseData).isEqualTo(command.responseData)
        assertThat(log.inputTokens).isEqualTo(command.inputTokens)
        assertThat(log.outputTokens).isEqualTo(command.outputTokens)
        assertThat(log.totalTokens).isEqualTo(command.totalTokens)
        assertThat(log.cachedInputTokens).isEqualTo(command.cachedInputTokens)
        assertThat(log.reasoningTokens).isEqualTo(command.reasoningTokens)
        assertThat(log.failureStage).isEqualTo(command.failureStage)
        assertThat(log.errorData).isEqualTo(command.errorData)
        verify(repository, never()).save(any(LlmCallLog::class.java))
    }

    @Test
    fun `사용량 없는 성공은 토큰을 합산하거나 영으로 변경하지 않는다`() {
        // given
        val log = log()
        given(repository.findById(12L)).willReturn(Optional.of(log))
        val command = CompleteLlmCallLog(LlmCallStatus.SUCCEEDED, log.startedAt.plusSeconds(1), 900)

        // when
        writer.complete(12L, command)

        // then
        assertThat(log.status).isEqualTo(LlmCallStatus.SUCCEEDED)
        assertThat(log.inputTokens).isNull()
        assertThat(log.outputTokens).isNull()
        assertThat(log.totalTokens).isNull()
        assertThat(log.failureStage).isNull()
        assertThat(log.errorData).isNull()
    }

    @Test
    fun `평가 ID 연결은 호출 성공 상태와 응답을 변경하지 않는다`() {
        // given
        val log = log().also { it.status = LlmCallStatus.SUCCEEDED; it.responseData = "{}" }
        given(repository.findById(12L)).willReturn(Optional.of(log))

        // when
        writer.linkEvaluationAttempt(12L, 99L)

        // then
        assertThat(log.evaluationAttemptId).isEqualTo(99L)
        assertThat(log.status).isEqualTo(LlmCallStatus.SUCCEEDED)
        assertThat(log.responseData).isEqualTo("{}")
    }

    @Test
    fun `시작 DB 오류는 무시하지 않고 호출자에게 전달한다`() {
        // given
        val command = StartLlmCallLog(
            LlmCallPurpose.ADMIN_PREVIEW, null, "gateway", "model",
            Instant.parse("2026-10-25T10:00:00Z"), "[]", "{}"
        )
        given(repository.save(any(LlmCallLog::class.java))).willThrow(IllegalStateException("database"))

        // when
        val action = { writer.start(command) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("database")
    }

    @Test
    fun `없는 행의 완료와 연결은 성공으로 무시하지 않는다`() {
        // given
        given(repository.findById(12L)).willReturn(Optional.empty())
        val command = CompleteLlmCallLog(LlmCallStatus.SUCCEEDED, Instant.now(), 1)

        // when
        val complete = { writer.complete(12L, command) }
        val link = { writer.linkEvaluationAttempt(12L, 1L) }

        // then
        assertThatThrownBy { complete() }.isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { link() }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `각 쓰기 진입점은 별도 트랜잭션을 선언한다`() {
        // given
        val methods = LlmCallLogWriter::class.java.declaredMethods.filter {
            it.name in listOf("start", "complete", "linkEvaluationAttempt")
        }

        // when
        val annotations = methods.map { it.getAnnotation(Transactional::class.java) }

        // then
        assertThat(annotations).hasSize(3).allSatisfy {
            assertThat(it).isNotNull()
            assertThat(it.propagation).isEqualTo(Propagation.REQUIRES_NEW)
        }
    }

    private fun log() = LlmCallLog(
        LlmCallPurpose.ANSWER_EVALUATION, 7L, "gateway", "model",
        Instant.parse("2026-10-25T10:00:00Z"), "[]", "{}"
    )
}
