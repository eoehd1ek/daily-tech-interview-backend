package com.eoehd1ek.tech.evaluation.application.model

data class EvaluationProviderResult(
    val criteria: List<CriterionResult>,
    val strengths: String,
    val weaknesses: String,
    val improvements: String,
) {
    data class CriterionResult(
        val criterionId: Long,
        val score: Int,
        val feedback: String,
    )
}
