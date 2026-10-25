package com.eoehd1ek.tech.llmcalllog.presentation

import com.eoehd1ek.tech.llmcalllog.application.LlmCallLogService
import com.eoehd1ek.tech.llmcalllog.presentation.request.LlmCallLogQuery
import com.eoehd1ek.tech.llmcalllog.presentation.response.LlmCallLogDetailResponse
import com.eoehd1ek.tech.llmcalllog.presentation.response.LlmCallLogPageResponse
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/llm-call-logs")
class LlmCallLogController(private val service: LlmCallLogService) {

    @GetMapping
    fun getLogs(
        @ModelAttribute
        query: LlmCallLogQuery
    ): LlmCallLogPageResponse {
        return LlmCallLogPageResponse.from(service.getLogs(query.toFilter()))
    }

    @GetMapping("/{logId}")
    fun getLog(
        @PathVariable("logId")
        @Min(1)
        @Max(9_007_199_254_740_991L)
        logId: Long,
    ): LlmCallLogDetailResponse {
        return LlmCallLogDetailResponse.from(service.getLog(logId))
    }
}
