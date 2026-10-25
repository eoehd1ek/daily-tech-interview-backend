package com.eoehd1ek.tech.llmcalllog.infrastructure.persistence

import com.eoehd1ek.tech.config.JpaAuditingConfig
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallFailureStage
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallLog
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallPurpose
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.Instant

@DataJpaTest
@Import(JpaAuditingConfig::class)
class LlmCallLogRepositoryTest {
    @Autowired
    private lateinit var repository: LlmCallLogRepository

    @Test
    fun `시작 기록을 저장하면 감사 시각과 원문을 유지하고 종료 정보는 null이다`() {
        // given
        val log = log(Instant.parse("2026-10-25T10:00:00Z"), "repository-start")
        val before = Instant.now()
        val createdBefore = log.createdAt
        repository.save(log)

        // when
        val result = repository.findById(requireNotNull(log.id)).orElseThrow()

        // then
        assertThat(createdBefore).isNull()
        assertThat(result.createdAt).isBetween(before, Instant.now())
        assertThat(result).usingRecursiveComparison().isEqualTo(log)
        assertThat(result.status).isEqualTo(LlmCallStatus.STARTED)
        assertThat(result.completedAt).isNull()
        assertThat(result.inputTokens).isNull()
        assertThat(result.evaluationAttemptId).isNull()
        assertThat(result.requestMessages).isEqualTo(" [{\"role\":\"user\",\"content\":\" raw \\n\"}] ")
    }

    @Test
    fun `완료 및 평가 연결 기록은 모든 응답과 토큰 필드를 유지한다`() {
        // given
        val log = log(Instant.parse("2026-10-25T10:00:00Z"), "repository-complete").also {
            it.status = LlmCallStatus.FAILED
            it.completedAt = it.startedAt.plusSeconds(2)
            it.durationMs = 1900
            it.responseId = "response-id"
            it.responseModel = "response-model"
            it.finishReason = "stop"
            it.responseData = "{\"results\":[],\"metadata\":{},\"usage\":null}"
            it.inputTokens = 100
            it.outputTokens = 30
            it.totalTokens = 130
            it.cachedInputTokens = 20
            it.reasoningTokens = 10
            it.failureStage = LlmCallFailureStage.RESPONSE_PARSE
            it.errorData = "{\"type\":\"Parse\",\"message\":null,\"causes\":[]}"
            it.evaluationAttemptId = 123
        }
        repository.save(log)

        // when
        val result = repository.findById(requireNotNull(log.id)).orElseThrow()

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(log)
        assertThat(result.responseData).isEqualTo(log.responseData)
        assertThat(result.errorData).isEqualTo(log.errorData)
    }

    @Test
    fun `목록은 시각과 ID 내림차순으로 정렬하고 페이지 크기와 오프셋을 적용한다`() {
        // given
        val time = Instant.parse("2026-10-25T10:00:00Z")
        val model = "repository-order"
        val first = repository.save(log(time, model))
        val second = repository.save(log(time, model))
        val newest = repository.save(log(time.plusSeconds(1), model))

        // when
        val firstPage = repository.findSummaries(null, null, model, null, null, 2, 0)
        val secondPage = repository.findSummaries(null, null, model, null, null, 2, 2)
        val total = repository.countMatching(null, null, model, null, null)

        // then
        assertThat(firstPage.map { it.id }).containsExactly(newest.id, second.id)
        assertThat(secondPage.map { it.id }).containsExactly(first.id)
        assertThat(firstPage.first().startedAt).isEqualTo(newest.startedAt)
        assertThat(firstPage.first().purpose).isEqualTo("ANSWER_EVALUATION")
        assertThat(firstPage.first().status).isEqualTo("STARTED")
        assertThat(firstPage.first().responseModel).isNull()
        assertThat(total).isEqualTo(3)
    }

    @Test
    fun `필터는 AND 결합하고 시작 경계는 포함하며 종료 경계는 제외한다`() {
        // given
        val from = Instant.parse("2026-10-25T10:00:00Z")
        val to = from.plusSeconds(100)
        val model = "repository-filter"
        val matching = log(from, model).also { it.status = LlmCallStatus.SUCCEEDED }
        repository.saveAll(
            listOf(
                matching,
                log(from.minusSeconds(1), model).also { it.status = LlmCallStatus.SUCCEEDED },
                log(to, model).also { it.status = LlmCallStatus.SUCCEEDED },
                log(from, model),
                log(from, model, LlmCallPurpose.ADMIN_PREVIEW).also { it.status = LlmCallStatus.SUCCEEDED },
                log(from, "repository-other").also { it.status = LlmCallStatus.SUCCEEDED },
            )
        )

        // when
        val result = repository.findSummaries("SUCCEEDED", "ANSWER_EVALUATION", model, from, to, 20, 0)
        val total = repository.countMatching("SUCCEEDED", "ANSWER_EVALUATION", model, from, to)

        // then
        assertThat(result.map { it.id }).containsExactly(matching.id)
        assertThat(total).isEqualTo(1)
    }

    @Test
    fun `필터 불일치와 아주 큰 페이지는 빈 목록을 반환하고 전체 건수는 유지한다`() {
        // given
        val model = "repository-empty"
        repository.save(log(Instant.parse("2026-10-25T10:00:00Z"), model))

        // when
        val beyond = repository.findSummaries(null, null, model, null, null, 100, 214_748_364_600L)
        val absent = repository.findSummaries("FAILED", null, model, null, null, 20, 0)
        val total = repository.countMatching(null, null, model, null, null)

        // then
        assertThat(beyond).isEmpty()
        assertThat(absent).isEmpty()
        assertThat(total).isEqualTo(1)
    }

    private fun log(
        startedAt: Instant,
        model: String,
        purpose: LlmCallPurpose = LlmCallPurpose.ANSWER_EVALUATION
    ): LlmCallLog {
        return LlmCallLog(
            purpose, if (purpose == LlmCallPurpose.ADMIN_PREVIEW) null else 7L,
            "gateway", model, startedAt, " [{\"role\":\"user\",\"content\":\" raw \\n\"}] ", "{}"
        )
    }
}
