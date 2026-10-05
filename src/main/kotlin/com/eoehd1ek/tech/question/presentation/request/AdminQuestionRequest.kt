package com.eoehd1ek.tech.question.presentation.request

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
    @AssertTrue(message = "평가 기준 배점 합은 100이고 순서는 중복이 없어야 합니다.")
    @JsonIgnore
    fun isValidCriteria(): Boolean =
        criteria.all { it != null } &&
                criteria.sumOf { it?.maxScore?.toLong() ?: 0L } == 100L &&
                criteria.map { it?.displayOrder }.distinct().size == criteria.size
}
