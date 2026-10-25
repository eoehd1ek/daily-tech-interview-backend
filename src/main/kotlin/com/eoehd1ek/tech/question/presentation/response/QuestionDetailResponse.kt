package com.eoehd1ek.tech.question.presentation.response

import com.eoehd1ek.tech.question.domain.QuestionCategory

data class QuestionDetailResponse(
    val id: Long,
    val title: String,
    val content: String,
    val categories: List<QuestionCategory>,
)
