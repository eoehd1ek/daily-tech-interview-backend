package com.eoehd1ek.tech.question.application.result

import com.eoehd1ek.tech.question.domain.EvaluationCriterion
import com.eoehd1ek.tech.question.domain.Question

data class AdminQuestionDetailResult(
    val question: Question,
    val criteria: List<EvaluationCriterion>,
)
