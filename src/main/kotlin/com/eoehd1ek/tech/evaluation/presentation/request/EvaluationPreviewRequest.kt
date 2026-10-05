package com.eoehd1ek.tech.evaluation.presentation.request

import com.eoehd1ek.tech.question.AdminCriterionRequest
import com.eoehd1ek.tech.question.AdminQuestionRequest
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class EvaluationPreviewRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,

    @field:NotBlank
    @field:Size(max = 10000)
    val content: String,

    @field:Valid
    @field:Size(min = 1, max = 10)
    val criteria: List<AdminCriterionRequest?>,

    @field:NotBlank
    @field:Size(max = 3000)
    val answer: String,
) {
    @AssertTrue
    @JsonIgnore
    fun isValidCriteria(): Boolean = AdminQuestionRequest(title, content, criteria).isValidCriteria()
}
