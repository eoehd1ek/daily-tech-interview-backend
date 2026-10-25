package com.eoehd1ek.tech.question.presentation.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AdminCriterionRequest(
    @field:NotBlank
    @field:Size(max = 1000)
    val content: String,

    @field:Min(1)
    @field:Max(100)
    val maxScore: Int,

    val displayOrder: Int,
)

fun List<AdminCriterionRequest?>.hasValidScoreAndOrder(): Boolean {
    return all { it != null } &&
            sumOf { it?.maxScore?.toLong() ?: 0L } == 100L &&
            map { it?.displayOrder }.distinct().size == size
}
