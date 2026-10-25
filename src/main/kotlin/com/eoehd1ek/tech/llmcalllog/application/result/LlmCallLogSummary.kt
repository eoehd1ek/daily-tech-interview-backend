package com.eoehd1ek.tech.llmcalllog.application.result

import com.eoehd1ek.tech.llmcalllog.domain.LlmCallFailureStage
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallLog
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallPurpose
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallStatus
import java.time.Instant

data class LlmCallLogSummary(
    val id: Long,
    val purpose: LlmCallPurpose,
    val status: LlmCallStatus,
    val provider: String,
    val requestedModel: String,
    val responseModel: String?,
    val startedAt: Instant,
    val completedAt: Instant?,
    val durationMs: Long?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val totalTokens: Long?,
    val failureStage: LlmCallFailureStage?,
) {
    companion object {
        fun from(log: LlmCallLog) = LlmCallLogSummary(
            checkNotNull(log.id),
            log.purpose,
            log.status,
            log.provider,
            log.requestedModel,
            log.responseModel,
            log.startedAt,
            log.completedAt,
            log.durationMs,
            log.inputTokens,
            log.outputTokens,
            log.totalTokens,
            log.failureStage,
        )
    }
}
