package com.eoehd1ek.tech.llmcalllog.application.model

import com.eoehd1ek.tech.llmcalllog.domain.LlmCallPurpose
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallStatus
import java.time.Instant

data class LlmCallLogFilter(
    val page: Int = 0,
    val size: Int = 20,
    val status: LlmCallStatus? = null,
    val purpose: LlmCallPurpose? = null,
    val model: String? = null,
    val startedFrom: Instant? = null,
    val startedTo: Instant? = null,
)
