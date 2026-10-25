package com.eoehd1ek.tech.llmcalllog.application.model

import com.eoehd1ek.tech.llmcalllog.domain.LlmCallPurpose
import java.time.Instant

data class StartLlmCallLog(
    val purpose: LlmCallPurpose,
    val questionId: Long?,
    val provider: String,
    val requestedModel: String,
    val startedAt: Instant,
    val requestMessages: String,
    val requestOptions: String,
)
