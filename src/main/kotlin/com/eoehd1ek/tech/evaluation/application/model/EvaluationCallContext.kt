package com.eoehd1ek.tech.evaluation.application.model

import com.eoehd1ek.tech.llmcalllog.domain.LlmCallPurpose

data class EvaluationCallContext(
    val purpose: LlmCallPurpose,
    val questionId: Long?,
)
