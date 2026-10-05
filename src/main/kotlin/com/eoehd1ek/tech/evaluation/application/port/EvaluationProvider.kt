package com.eoehd1ek.tech.evaluation.application.port

import com.eoehd1ek.tech.evaluation.application.model.EvaluationCriterionSpec
import com.eoehd1ek.tech.evaluation.application.model.EvaluationProviderResult

interface EvaluationProvider {
    fun evaluate(
        title: String,
        content: String,
        criteria: List<EvaluationCriterionSpec>,
        answer: String,
    ): EvaluationProviderResult
}
