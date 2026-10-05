package com.eoehd1ek.tech.evaluation

data class EvaluatedAnswer(
    val score: Int,
    val result: EvaluationResult,
    val strengths: String,
    val weaknesses: String,
    val improvements: String,
)
