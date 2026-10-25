package com.eoehd1ek.tech.question.presentation.response

import com.eoehd1ek.tech.question.domain.QuestionCategory

data class QuestionResponse(
    val id: Long,
    val title: String,
    val categories: List<QuestionCategory>,
)
