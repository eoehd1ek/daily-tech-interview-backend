package com.eoehd1ek.tech.evaluation

data class EvaluationPreviewResponse(
    val questionTitle: String,
    val answer: String,
    val score: Int,
    val result: EvaluationResult,
    val strengths: String,
    val weaknesses: String,
    val improvements: String,
)
