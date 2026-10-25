package com.eoehd1ek.tech.question.presentation.response

import com.eoehd1ek.tech.question.domain.EvaluationCriterion
import com.eoehd1ek.tech.question.domain.Question
import com.eoehd1ek.tech.question.domain.QuestionCategory

data class AdminQuestionResponse(
    val id: Long,
    val title: String,
    val content: String,
    val criteria: List<AdminCriterionResponse>,
    val categories: List<QuestionCategory>,
) {

    companion object {
        fun from(
            question: Question,
            criteria: List<EvaluationCriterion>,
            categories: List<QuestionCategory>
        ): AdminQuestionResponse {
            return AdminQuestionResponse(
                id = checkNotNull(question.id),
                title = question.title,
                content = question.content,
                criteria = criteria.sortedWith(compareBy<EvaluationCriterion> { it.displayOrder }.thenBy { it.id })
                    .map {
                        AdminCriterionResponse(checkNotNull(it.id), it.content, it.maxScore, it.displayOrder)
                    },
                categories = categories.sortedBy { it.ordinal },
            )
        }
    }
}
