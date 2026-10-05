package com.eoehd1ek.tech.question.presentation.response

data class AdminCriterionResponse(
    val id: Long,
    val content: String,
    val maxScore: Int,
    val displayOrder: Int,
)
