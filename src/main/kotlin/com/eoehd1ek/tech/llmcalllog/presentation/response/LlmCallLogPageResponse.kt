package com.eoehd1ek.tech.llmcalllog.presentation.response

import com.eoehd1ek.tech.llmcalllog.application.result.LlmCallLogPage
import com.eoehd1ek.tech.llmcalllog.application.result.LlmCallLogSummary

data class LlmCallLogPageResponse(
    val items: List<LlmCallLogSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Long,
) {

    companion object {
        fun from(result: LlmCallLogPage): LlmCallLogPageResponse {
            return LlmCallLogPageResponse(
                result.items,
                result.page,
                result.size,
                result.totalElements,
                result.totalPages,
            )
        }
    }
}
