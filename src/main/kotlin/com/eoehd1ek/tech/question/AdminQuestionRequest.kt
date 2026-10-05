package com.eoehd1ek.tech.question

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AdminQuestionRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,

    @field:NotBlank
    @field:Size(max = 10000)
    val content: String,

    @field:Valid
    @field:Size(min = 1, max = 10)
    val criteria: List<AdminCriterionRequest?>,
) {
    @AssertTrue(message = "criteria의 maxScore 합은 100이어야 합니다.")
    @JsonIgnore
    fun isValidCriteria(): Boolean =
        criteria.all { it != null } &&
                criteria.sumOf { it?.maxScore?.toLong() ?: 0L } == 100L
}
