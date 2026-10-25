package com.eoehd1ek.tech.llmcalllog.application.result

data class LlmCallLogPage(
    val items: List<LlmCallLogSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Long,
)
