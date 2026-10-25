package com.eoehd1ek.tech.llmcalllog.infrastructure.persistence

import java.time.Instant

interface LlmCallLogSummaryProjection {
    val id: Long
    val purpose: String
    val status: String
    val provider: String
    val requestedModel: String
    val responseModel: String?
    val startedAt: Instant
    val completedAt: Instant?
    val durationMs: Long?
    val inputTokens: Long?
    val outputTokens: Long?
    val totalTokens: Long?
    val failureStage: String?
}
