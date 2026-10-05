package com.eoehd1ek.tech.evaluation.application

import com.eoehd1ek.tech.question.EvaluationCriterion

data class EvaluationCriterionSpec(
    val id: Long,
    val content: String,
    val maxScore: Int,
) {
    companion object {
        fun from(criterion: EvaluationCriterion): EvaluationCriterionSpec =
            EvaluationCriterionSpec(checkNotNull(criterion.id), criterion.content, criterion.maxScore)
    }
}
