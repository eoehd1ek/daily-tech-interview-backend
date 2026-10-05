package com.eoehd1ek.tech.evaluation.application.validation

import com.eoehd1ek.tech.evaluation.application.exception.LlmEvaluationFailedException
import com.eoehd1ek.tech.evaluation.application.model.EvaluationCriterionSpec
import com.eoehd1ek.tech.evaluation.application.model.EvaluationProviderResult
import com.eoehd1ek.tech.evaluation.application.model.EvaluationProviderResult.CriterionResult
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

class EvaluationProviderResultValidatorTest {
    private val validator = EvaluationProviderResultValidator()

    @Test
    fun `응답 순서와 무관하게 ID별 기준을 찾아 영점과 상한 점수를 허용한다`() {
        // given
        val criteria = listOf(EvaluationCriterionSpec(10L, "첫 기준", 30), EvaluationCriterionSpec(20L, "둘째 기준", 70))
        val response = response(CriterionResult(20L, 70, "둘째 피드백"), CriterionResult(10L, 0, "첫 피드백"))

        // when
        val action = { validator.validate(response, criteria) }

        // then
        assertThatCode { action() }.doesNotThrowAnyException()
    }

    @ParameterizedTest
    @CsvSource("10, -1", "10, 31", "20, -1", "20, 71")
    fun `점수가 음수이거나 해당 ID의 상한을 초과하면 거부한다`(id: Long, score: Int) {
        // given
        val criteria = listOf(EvaluationCriterionSpec(10L, "첫 기준", 30), EvaluationCriterionSpec(20L, "둘째 기준", 70))
        val response = response(
            CriterionResult(10L, if (id == 10L) score else 0, "첫 피드백"),
            CriterionResult(20L, if (id == 20L) score else 0, "둘째 피드백"),
        )

        // when
        val action = { validator.validate(response, criteria) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(LlmEvaluationFailedException::class.java)
    }

    @ParameterizedTest
    @MethodSource("invalidCriteria")
    fun `항목 개수가 다르거나 중복 ID 또는 등록되지 않은 ID가 있으면 거부한다`(items: List<CriterionResult>) {
        // given
        val criteria = listOf(EvaluationCriterionSpec(10L, "첫 기준", 40), EvaluationCriterionSpec(20L, "둘째 기준", 60))
        val response = EvaluationProviderResult(items, "강점", "약점", "개선")

        // when
        val action = { validator.validate(response, criteria) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(LlmEvaluationFailedException::class.java)
    }

    private fun response(vararg items: CriterionResult): EvaluationProviderResult =
        EvaluationProviderResult(items.toList(), "강점", "약점", "개선")

    companion object {
        @JvmStatic
        fun invalidCriteria(): List<Arguments> = listOf(
            emptyList<Long>(),
            listOf(10L),
            listOf(10L, 10L),
            listOf(10L, 30L),
            listOf(10L, 20L, 30L),
        ).map { ids -> Arguments.of(ids.map { CriterionResult(it, 0, "항목 피드백") }) }
    }
}
