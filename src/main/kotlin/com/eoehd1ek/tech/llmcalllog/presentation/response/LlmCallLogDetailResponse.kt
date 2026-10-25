package com.eoehd1ek.tech.llmcalllog.presentation.response

import com.eoehd1ek.tech.llmcalllog.domain.LlmCallFailureStage
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallLog
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallPurpose
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallStatus
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

data class LlmCallLogDetailResponse(
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
    val questionId: Long?,
    val evaluationAttemptId: Long?,
    val createdAt: Instant,
    val responseId: String?,
    val finishReason: String?,
    val cachedInputTokens: Long?,
    val reasoningTokens: Long?,
    val requestMessages: JsonNode,
    val requestOptions: JsonNode,
    val responseData: JsonNode?,
    val errorData: JsonNode?,
) {

    companion object {
        private val mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build()

        fun from(log: LlmCallLog) = LlmCallLogDetailResponse(
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
            log.questionId,
            log.evaluationAttemptId,
            checkNotNull(log.createdAt),
            log.responseId,
            log.finishReason,
            log.cachedInputTokens,
            log.reasoningTokens,
            snapshot(
                log.requestMessages,
                array = true
            ),
            snapshot(log.requestOptions),
            log.responseData?.let { snapshot(it) },
            log.errorData?.let { snapshot(it) },
        )

        private fun snapshot(
            value: String,
            array: Boolean = false
        ): JsonNode {
            val node = mapper.readTree(value)
            check(node != null && (if (array) node.isArray else node.isObject)) { "Invalid log snapshot" }
            return node
        }
    }
}
