package com.eoehd1ek.tech.evaluation

import com.eoehd1ek.tech.question.EvaluationCriterion
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.test.util.ReflectionTestUtils

class EvaluationResponseValidatorTest {
    private val validator = EvaluationResponseValidator()

    @ParameterizedTest
    @CsvSource("0, FAIL", "49, FAIL", "50, RETRY", "79, RETRY", "80, PASS", "100, PASS")
    fun `점수 경계에 따라 서버가 합계와 평가 결과를 결정한다`(score: Int, expected: EvaluationResult) {
        // given
        val criteria = listOf(criterion(10L, 100))
        val content = response("""{"criterionId":10,"score":$score,"feedback":"항목 피드백"}""")

        // when
        val result = validator.validate(content, criteria)

        // then
        assertThat(result).isEqualTo(EvaluatedAnswer(score, expected, "강점", "약점", "개선"))
    }

    @Test
    fun `응답의 항목 순서와 무관하게 ID로 기준별 상한을 적용하고 점수를 합산한다`() {
        // given
        val criteria = listOf(criterion(10L, 30), criterion(20L, 70))
        val content = response(
            """{"criterionId":20,"score":65,"feedback":"두 번째 항목"},
                {"criterionId":10,"score":15,"feedback":"첫 번째 항목"}""",
        )

        // when
        val result = validator.validate(content, criteria)

        // then
        assertThat(result).isEqualTo(EvaluatedAnswer(80, EvaluationResult.PASS, "강점", "약점", "개선"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "\"total\":100,\"totalScore\":100,\"score\":100,\"result\":\"PASS\"",
            "\"total\":null,\"totalScore\":\"잘못된 합계\",\"score\":{},\"result\":[]",
        ],
    )
    fun `LLM이 추가한 합계와 결과 필드는 무시하고 항목 점수로 평가한다`(extraFields: String) {
        // given
        val criteria = listOf(criterion(10L, 100))
        val content = response("""{"criterionId":10,"score":49,"feedback":"항목 피드백"}""")
            .dropLast(1) + ",$extraFields}"

        // when
        val result = validator.validate(content, criteria)

        // then
        assertThat(result).isEqualTo(EvaluatedAnswer(49, EvaluationResult.FAIL, "강점", "약점", "개선"))
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
        strings = ["-1", "101", "80.5", "80.0", "\"80\"", "null", "2147483648", "9223372036854775808", "true", "{}", "[]"],
    )
    fun `점수가 누락되거나 정수 범위와 항목 상한을 지키지 않으면 거부한다`(score: String?) {
        // given
        val criteria = listOf(criterion(10L, 100))
        val scoreField = score?.let { "\"score\":$it," }.orEmpty()
        val content = response("""{$scoreField"criterionId":10,"feedback":"항목 피드백"}""")

        // when
        val action = { validator.validate(content, criteria) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(InvalidLlmResponseException::class.java)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["20", "10.5", "10.0", "\"10\"", "null", "9223372036854775808", "true", "{}", "[]"])
    fun `기준 ID가 누락되거나 알 수 없거나 정수 타입이 아니면 거부한다`(id: String?) {
        // given
        val criteria = listOf(criterion(10L, 100))
        val idField = id?.let { "\"criterionId\":$it," }.orEmpty()
        val content = response("""{$idField"score":80,"feedback":"항목 피드백"}""")

        // when
        val action = { validator.validate(content, criteria) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(InvalidLlmResponseException::class.java)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            """{"criterionId":10,"score":30,"feedback":"피드백"}""",
            """{"criterionId":10,"score":30,"feedback":"피드백"},{"criterionId":10,"score":30,"feedback":"피드백"}""",
            """{"criterionId":10,"score":30,"feedback":"피드백"},{"criterionId":30,"score":60,"feedback":"피드백"}""",
            """{"criterionId":10,"score":30,"feedback":"피드백"},{"criterionId":20,"score":60,"feedback":"피드백"},{"criterionId":30,"score":0,"feedback":"피드백"}""",
        ],
    )
    fun `평가 항목이 누락되거나 중복되거나 등록되지 않은 ID를 포함하면 거부한다`(items: String) {
        // given
        val criteria = listOf(criterion(10L, 40), criterion(20L, 60))
        val content = response(items)

        // when
        val action = { validator.validate(content, criteria) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(InvalidLlmResponseException::class.java)
    }

    @ParameterizedTest
    @MethodSource("invalidFeedbackResponses")
    fun `항목과 전체 피드백이 누락되거나 공백이거나 문자열이 아니면 거부한다`(field: String, content: String) {
        // given
        val criteria = listOf(criterion(10L, 100))

        // when
        val action = { validator.validate(content, criteria) }

        // then
        assertThatThrownBy { action() }.describedAs("피드백 필드: %s", field)
            .isInstanceOf(InvalidLlmResponseException::class.java)
    }

    @ParameterizedTest
    @MethodSource("invalidJsonResponses")
    fun `잘못된 JSON과 객체 또는 배열 계약 위반과 중복 키를 거부한다`(content: String) {
        // given
        val criteria = listOf(criterion(10L, 100))

        // when
        val action = { validator.validate(content, criteria) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(InvalidLlmResponseException::class.java)
    }

    private fun criterion(id: Long, maxScore: Int): EvaluationCriterion {
        val criterion = EvaluationCriterion(questionId = 1L, content = "평가 기준", maxScore = maxScore, displayOrder = 1)
        ReflectionTestUtils.setField(criterion, "id", id)
        return criterion
    }

    companion object {
        private fun response(items: String = """{"feedback":"항목 피드백","criterionId":10,"score":80}"""): String =
            """{"strengths":"강점","weaknesses":"약점","improvements":"개선","criteria":[$items]}"""

        @JvmStatic
        fun invalidFeedbackResponses(): List<Arguments> {
            val fields = mapOf("feedback" to "항목 피드백", "strengths" to "강점", "weaknesses" to "약점", "improvements" to "개선")
            val values = listOf(null, "\"\"", "\" \\t\\n\"", "null", "123", "true", "[]", "{}")
            return fields.flatMap { (field, validValue) ->
                values.map { value ->
                    val replacement = value?.let { "\"$field\":$it," }.orEmpty()
                    Arguments.of(field, response().replace("\"$field\":\"$validValue\",", replacement))
                }
            }
        }

        @JvmStatic
        fun invalidJsonResponses(): List<String> {
            val valid = response()
            return listOf(
                "",
                " ",
                "{",
                valid.dropLast(1),
                "[]",
                "null",
                "true",
                "80",
                "\"응답\"",
                valid.replace("\"criteria\":[", "\"criteria\":null,\"ignored\":["),
                valid.replace("\"criteria\":[", "\"ignored\":["),
                valid.replace("\"criteria\":[", "\"criteria\":{},\"ignored\":["),
                valid.replace("\"criteria\":[", "\"criteria\":\"항목\",\"ignored\":["),
                valid.replace("\"criteria\":[", "\"criteria\":80,\"ignored\":["),
                response("null"),
                response("80"),
                response("[]"),
                response("\"항목\""),
                "$valid {}",
                "$valid null",
                "$valid trailing",
                valid.replace("\"strengths\":", "\"strengths\":\"중복\",\"strengths\":"),
                valid.replace("\"criteria\":", "\"criteria\":[],\"criteria\":"),
                valid.replace("\"criterionId\":10", "\"criterionId\":20,\"criterionId\":10"),
                valid.replace("\"score\":80", "\"score\":0,\"score\":80"),
                valid.replace("\"feedback\":", "\"feedback\":\"중복\",\"feedback\":"),
            )
        }
    }
}
