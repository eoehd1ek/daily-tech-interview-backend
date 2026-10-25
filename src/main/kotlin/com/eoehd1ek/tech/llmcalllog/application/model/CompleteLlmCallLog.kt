package com.eoehd1ek.tech.llmcalllog.application.model

import com.eoehd1ek.tech.llmcalllog.domain.LlmCallFailureStage
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallStatus
import java.time.Instant

data class CompleteLlmCallLog(
    val status: LlmCallStatus,
    val completedAt: Instant,
    val durationMs: Long,
    val responseId: String? = null,
    val responseModel: String? = null,
    val finishReason: String? = null,
    val responseData: String? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val reasoningTokens: Long? = null,
    val failureStage: LlmCallFailureStage? = null,
    val errorData: String? = null,
)
