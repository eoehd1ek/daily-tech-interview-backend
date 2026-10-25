package com.eoehd1ek.tech.llmcalllog.application

import com.eoehd1ek.tech.llmcalllog.application.exception.LlmCallLogNotFoundException
import com.eoehd1ek.tech.llmcalllog.application.model.LlmCallLogFilter
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallFailureStage
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallLog
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallPurpose
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallStatus
import com.eoehd1ek.tech.llmcalllog.infrastructure.persistence.LlmCallLogRepository
import com.eoehd1ek.tech.llmcalllog.infrastructure.persistence.LlmCallLogSummaryProjection
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class LlmCallLogServiceTest {
    @Mock
    private lateinit var repository: LlmCallLogRepository

    @InjectMocks
    private lateinit var service: LlmCallLogService

    @Test
    fun `목록은 모든 필터와 Long 오프셋을 적용하고 요약 및 실제 전체 건수를 반환한다`() {
        // given
        val from = Instant.parse("2026-10-25T00:00:00Z")
        val to = from.plusSeconds(86400)
        val filter = LlmCallLogFilter(
            2, 20, LlmCallStatus.FAILED, LlmCallPurpose.ADMIN_PREVIEW,
            "model", from, to
        )
        val projection = object : LlmCallLogSummaryProjection {
            override val id = 12L
            override val purpose = "ADMIN_PREVIEW"
            override val status = "FAILED"
            override val provider = "gateway"
            override val requestedModel = "model"
            override val responseModel: String? = null
            override val startedAt = from.plusSeconds(100)
            override val completedAt = from.plusSeconds(102)
            override val durationMs = 1900L
            override val inputTokens = 0L
            override val outputTokens: Long? = null
            override val totalTokens: Long? = null
            override val failureStage = "MODEL_CALL"
        }
        given(repository.findSummaries("FAILED", "ADMIN_PREVIEW", "model", from, to, 20, 40))
            .willReturn(listOf(projection))
        given(repository.countMatching("FAILED", "ADMIN_PREVIEW", "model", from, to)).willReturn(41)

        // when
        val result = service.getLogs(filter)

        // then
        assertThat(result.page).isEqualTo(2)
        assertThat(result.size).isEqualTo(20)
        assertThat(result.totalElements).isEqualTo(41)
        assertThat(result.totalPages).isEqualTo(3)
        assertThat(result.items).hasSize(1)
        assertThat(result.items.single().status).isEqualTo(LlmCallStatus.FAILED)
        assertThat(result.items.single().purpose).isEqualTo(LlmCallPurpose.ADMIN_PREVIEW)
        assertThat(result.items.single().failureStage).isEqualTo(LlmCallFailureStage.MODEL_CALL)
        assertThat(result.items.single().inputTokens).isZero()
        assertThat(result.items.single().totalTokens).isNull()
    }

    @Test
    fun `최대 페이지는 Int 범위를 넘는 오프셋을 유지하고 빈 항목과 전체 건수를 반환한다`() {
        // given
        val filter = LlmCallLogFilter(2_147_483_646, 100)
        given(repository.findSummaries(null, null, null, null, null, 100, 214_748_364_600L))
            .willReturn(emptyList())
        given(repository.countMatching(null, null, null, null, null)).willReturn(100)

        // when
        val result = service.getLogs(filter)

        // then
        assertThat(result.items).isEmpty()
        assertThat(result.totalElements).isEqualTo(100)
        assertThat(result.totalPages).isEqualTo(1)
    }

    @Test
    fun `최초 빈 목록은 전체 건수와 페이지 수 영을 반환한다`() {
        // given
        given(repository.findSummaries(null, null, null, null, null, 20, 0)).willReturn(emptyList())
        given(repository.countMatching(null, null, null, null, null)).willReturn(0)

        // when
        val result = service.getLogs(LlmCallLogFilter())

        // then
        assertThat(result.items).isEmpty()
        assertThat(result.totalElements).isZero()
        assertThat(result.totalPages).isZero()
    }

    @Test
    fun `상세는 저장된 기록을 조회하고 없는 ID는 전용 예외를 반환한다`() {
        // given
        val log = LlmCallLog(LlmCallPurpose.ADMIN_PREVIEW, null, "gateway", "model", Instant.now(), "[]", "{}")
        ReflectionTestUtils.setField(log, "id", 12L)
        given(repository.findById(12L)).willReturn(Optional.of(log))
        given(repository.findById(13L)).willReturn(Optional.empty())

        // when
        val result = service.getLog(12L)
        val missing = { service.getLog(13L) }

        // then
        assertThat(result.id).isEqualTo(12L)
        assertThat(result.requestMessages).isEqualTo("[]")
        assertThatThrownBy { missing() }.isInstanceOf(LlmCallLogNotFoundException::class.java)
        verify(repository).findById(12L)
    }
}
