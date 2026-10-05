package com.eoehd1ek.tech.question

data class AdminQuestionResponse(
    val id: Long,
    val title: String,
    val content: String,
    val criteria: List<AdminCriterionResponse>,
) {
    companion object {
        fun from(question: Question, criteria: List<EvaluationCriterion>): AdminQuestionResponse =
            AdminQuestionResponse(
                id = checkNotNull(question.id),
                title = question.title,
                content = question.content,
                criteria = criteria.map {
                    AdminCriterionResponse(checkNotNull(it.id), it.content, it.maxScore, it.displayOrder)
                },
            )
    }
}
