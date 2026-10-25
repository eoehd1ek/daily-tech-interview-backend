package com.eoehd1ek.tech.question.presentation.request

import com.eoehd1ek.tech.question.domain.QuestionCategory
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import tools.jackson.databind.annotation.JsonDeserialize

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

    @field:Size(max = 5)
    @field:JsonDeserialize(using = QuestionCategoriesDeserializer::class)
    val categories: List<QuestionCategory>,
) {

    @AssertTrue(message = "평가 기준 배점 합은 100이고 순서는 중복이 없어야 합니다.")
    @JsonIgnore
    fun isValidCriteria(): Boolean {
        return criteria.hasValidScoreAndOrder()
    }
}
