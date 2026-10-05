package com.eoehd1ek.tech.evaluation.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class EvaluationResultTest {
    @ParameterizedTest
    @CsvSource("-1, FAIL", "0, FAIL", "49, FAIL", "50, RETRY", "79, RETRY", "80, PASS", "100, PASS", "101, PASS")
    fun `점수가 50 미만이면 FAIL 80 미만이면 RETRY 나머지는 PASS로 판정한다`(score: Int, expected: EvaluationResult) {
        // given
        val totalScore = score

        // when
        val result = EvaluationResult.fromScore(totalScore)

        // then
        assertThat(result).isEqualTo(expected)
    }
}
