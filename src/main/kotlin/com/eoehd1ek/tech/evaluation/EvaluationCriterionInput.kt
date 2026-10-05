package com.eoehd1ek.tech.evaluation

import com.eoehd1ek.tech.question.EvaluationCriterion

data class EvaluationCriterionInput(
    val id: Long,
    val content: String,
    val maxScore: Int,
) {
    companion object {
        fun from(criterion: EvaluationCriterion): EvaluationCriterionInput =
            EvaluationCriterionInput(checkNotNull(criterion.id), criterion.content, criterion.maxScore)
    }
}
