package com.eoehd1ek.tech.evaluation.application.validation

import com.eoehd1ek.tech.evaluation.application.exception.LlmEvaluationFailedException
import com.eoehd1ek.tech.evaluation.application.model.EvaluationCriterionSpec
import com.eoehd1ek.tech.evaluation.application.model.EvaluationProviderResult
import org.springframework.stereotype.Component

@Component
class EvaluationProviderResultValidator {
    fun validate(response: EvaluationProviderResult, criteria: List<EvaluationCriterionSpec>) {
        val expectedById = criteria.associateBy { it.id }
        val actualIds = response.criteria.map { it.criterionId }
        if (
            actualIds.size != expectedById.size ||
            actualIds.toSet() != expectedById.keys
        ) {
            throw LlmEvaluationFailedException()
        }

        val hasInvalidScore = response.criteria.any { (criterionId, score) ->
            val criterion = expectedById.getValue(criterionId)
            score !in 0..criterion.maxScore
        }

        if (hasInvalidScore) {
            throw LlmEvaluationFailedException()
        }
    }
}
