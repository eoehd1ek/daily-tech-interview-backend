package com.eoehd1ek.tech.evaluation.application.result

import com.eoehd1ek.tech.evaluation.domain.EvaluationResult

data class EvaluatedAnswerResult(
    val score: Int,
    val result: EvaluationResult,
    val strengths: String,
    val weaknesses: String,
    val improvements: String,
)
