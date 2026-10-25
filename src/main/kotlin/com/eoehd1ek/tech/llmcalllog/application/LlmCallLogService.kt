package com.eoehd1ek.tech.llmcalllog.application

import com.eoehd1ek.tech.llmcalllog.application.exception.LlmCallLogNotFoundException
import com.eoehd1ek.tech.llmcalllog.application.model.LlmCallLogFilter
import com.eoehd1ek.tech.llmcalllog.application.result.LlmCallLogPage
import com.eoehd1ek.tech.llmcalllog.application.result.LlmCallLogSummary
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallFailureStage
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallLog
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallPurpose
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallStatus
import com.eoehd1ek.tech.llmcalllog.infrastructure.persistence.LlmCallLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class LlmCallLogService(private val repository: LlmCallLogRepository) {

    fun getLogs(filter: LlmCallLogFilter): LlmCallLogPage {
        val items = repository.findSummaries(
            filter.status?.name,
            filter.purpose?.name,
            filter.model,
            filter.startedFrom,
            filter.startedTo,
            filter.size,
            filter.page.toLong() * filter.size,
        ).map {
            LlmCallLogSummary(
                it.id,
                LlmCallPurpose.valueOf(it.purpose),
                LlmCallStatus.valueOf(it.status),
                it.provider,
                it.requestedModel,
                it.responseModel,
                it.startedAt,
                it.completedAt,
                it.durationMs,
                it.inputTokens,
                it.outputTokens,
                it.totalTokens,
                it.failureStage?.let(LlmCallFailureStage::valueOf),
            )
        }
        val total = repository.countMatching(
            filter.status?.name,
            filter.purpose?.name,
            filter.model,
            filter.startedFrom,
            filter.startedTo,
        )
        return LlmCallLogPage(
            items,
            filter.page,
            filter.size,
            total,
            total / filter.size + if (total % filter.size == 0L) 0 else 1
        )
    }

    fun getLog(id: Long): LlmCallLog {
        return repository.findById(id)
            .orElseThrow(::LlmCallLogNotFoundException)
    }
}
