package com.eoehd1ek.tech.evaluation.application.model

import com.eoehd1ek.tech.question.domain.EvaluationCriterion

data class EvaluationCriterionSpec(
    val id: Long,
    val content: String,
    val maxScore: Int,
) {
    companion object {
        fun from(criterion: EvaluationCriterion): EvaluationCriterionSpec =
            EvaluationCriterionSpec(
                id = checkNotNull(criterion.id),
                content = criterion.content,
                maxScore = criterion.maxScore
            )
    }
}
