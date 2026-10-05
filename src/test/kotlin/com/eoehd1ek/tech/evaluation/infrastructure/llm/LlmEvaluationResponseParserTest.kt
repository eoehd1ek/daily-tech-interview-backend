package com.eoehd1ek.tech.evaluation.infrastructure.llm

import com.eoehd1ek.tech.evaluation.application.model.EvaluationProviderResult
import com.eoehd1ek.tech.evaluation.application.model.EvaluationProviderResult.CriterionResult
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

class LlmEvaluationResponseParserTest {
    private val parser = LlmEvaluationResponseParser()

    @Test
    fun `응답의 항목 순서와 점수 및 피드백을 그대로 파싱한다`() {
        // given
        val content = response(
            """{"criterionId":20,"score":65,"feedback":"두 번째 항목"},
                {"criterionId":10,"score":15,"feedback":"첫 번째 항목"}""",
        )

        // when
        val result = parser.parse(content)

        // then
        assertThat(result).isEqualTo(
            EvaluationProviderResult(
                listOf(CriterionResult(20L, 65, "두 번째 항목"), CriterionResult(10L, 15, "첫 번째 항목")),
                "강점", "약점", "개선",
            ),
        )
    }

    @Test
    fun `중복 ID와 점수 범위는 검사하지 않고 파싱한다`() {
        // given
        val content = response(
            """{"criterionId":30,"score":-1,"feedback":"음수 점수"},
                {"criterionId":30,"score":101,"feedback":"상한 초과 점수"}""",
        )

        // when
        val result = parser.parse(content)

        // then
        assertThat(result.criteria).containsExactly(
            CriterionResult(30L, -1, "음수 점수"), CriterionResult(30L, 101, "상한 초과 점수"),
        )
    }

    @Test
    fun `평가 항목이 비어 있어도 배열 형식이면 파싱한다`() {
        // given
        val content = response("")

        // when
        val result = parser.parse(content)

        // then
        assertThat(result.criteria).isEmpty()
    }

    @ParameterizedTest
    @CsvSource("-9223372036854775808, -2147483648", "9223372036854775807, 2147483647")
    fun `ID는 Long 점수는 Int 범위의 정수를 손실 없이 파싱한다`(id: Long, score: Int) {
        // given
        val content = response("""{"criterionId":$id,"score":$score,"feedback":"항목 피드백"}""")

        // when
        val result = parser.parse(content)

        // then
        assertThat(result.criteria).containsExactly(CriterionResult(id, score, "항목 피드백"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "\"total\":100,\"totalScore\":100,\"score\":100,\"result\":\"PASS\"",
            "\"total\":null,\"totalScore\":\"잘못된 합계\",\"score\":{},\"result\":[]",
        ],
    )
    fun `LLM이 추가한 합계와 결과 필드는 무시한다`(extraFields: String) {
        // given
        val content = response("""{"criterionId":10,"score":49,"feedback":"항목 피드백"}""")
            .dropLast(1) + ",$extraFields}"

        // when
        val result = parser.parse(content)

        // then
        assertThat(result).isEqualTo(
            EvaluationProviderResult(listOf(CriterionResult(10L, 49, "항목 피드백")), "강점", "약점", "개선"),
        )
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
        strings = [
            "80.5", "80.0", "8e1", "\"80\"", "null", "-2147483649", "2147483648",
            "9223372036854775808", "true", "{}", "[]",
        ],
    )
    fun `점수가 누락되거나 Int 범위의 정수 타입이 아니면 거부한다`(score: String?) {
        // given
        val scoreField = score?.let { "\"score\":$it," }.orEmpty()
        val content = response("""{$scoreField"criterionId":10,"feedback":"항목 피드백"}""")

        // when
        val action = { parser.parse(content) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(InvalidLlmResponseException::class.java)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
        strings = [
            "10.5", "10.0", "1e1", "\"10\"", "null", "-9223372036854775809", "9223372036854775808",
            "true", "{}", "[]",
        ],
    )
    fun `기준 ID가 누락되거나 Long 범위의 정수 타입이 아니면 거부한다`(id: String?) {
        // given
        val idField = id?.let { "\"criterionId\":$it," }.orEmpty()
        val content = response("""{$idField"score":80,"feedback":"항목 피드백"}""")

        // when
        val action = { parser.parse(content) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(InvalidLlmResponseException::class.java)
    }

    @ParameterizedTest
    @MethodSource("invalidFeedbackResponses")
    fun `항목과 전체 피드백이 누락되거나 공백이거나 문자열이 아니면 거부한다`(field: String, content: String) {
        // given
        val responseContent = content

        // when
        val action = { parser.parse(responseContent) }

        // then
        assertThatThrownBy { action() }.describedAs("피드백 필드: %s", field)
            .isInstanceOf(InvalidLlmResponseException::class.java)
    }

    @ParameterizedTest
    @MethodSource("invalidJsonResponses")
    fun `잘못된 JSON과 객체 또는 배열 계약 위반과 중복 키를 거부한다`(content: String) {
        // given
        val responseContent = content

        // when
        val action = { parser.parse(responseContent) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(InvalidLlmResponseException::class.java)
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
