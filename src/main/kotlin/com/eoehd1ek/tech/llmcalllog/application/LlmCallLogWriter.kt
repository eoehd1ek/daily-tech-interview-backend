package com.eoehd1ek.tech.llmcalllog.application

import com.eoehd1ek.tech.llmcalllog.application.model.CompleteLlmCallLog
import com.eoehd1ek.tech.llmcalllog.application.model.StartLlmCallLog
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallLog
import com.eoehd1ek.tech.llmcalllog.infrastructure.persistence.LlmCallLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class LlmCallLogWriter(private val repository: LlmCallLogRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun start(command: StartLlmCallLog): Long {
        val saved = repository.save(
            LlmCallLog(
                purpose = command.purpose,
                questionId = command.questionId,
                provider = command.provider,
                requestedModel = command.requestedModel,
                startedAt = command.startedAt,
                requestMessages = command.requestMessages,
                requestOptions = command.requestOptions,
            )
        )
        checkNotNull(saved.createdAt)
        return checkNotNull(saved.id)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun complete(id: Long, command: CompleteLlmCallLog) {
        val log = checkNotNull(repository.findById(id).orElse(null))
        log.status = command.status
        log.completedAt = command.completedAt
        log.durationMs = command.durationMs
        log.responseId = command.responseId
        log.responseModel = command.responseModel
        log.finishReason = command.finishReason
        log.responseData = command.responseData
        log.inputTokens = command.inputTokens
        log.outputTokens = command.outputTokens
        log.totalTokens = command.totalTokens
        log.cachedInputTokens = command.cachedInputTokens
        log.reasoningTokens = command.reasoningTokens
        log.failureStage = command.failureStage
        log.errorData = command.errorData
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun linkEvaluationAttempt(id: Long, attemptId: Long) {
        val log = checkNotNull(repository.findById(id).orElse(null))
        log.evaluationAttemptId = attemptId
    }
}
