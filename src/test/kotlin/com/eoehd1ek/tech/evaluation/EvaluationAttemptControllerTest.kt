package com.eoehd1ek.tech.evaluation

import com.eoehd1ek.tech.question.QuestionNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@WebMvcTest(EvaluationAttemptController::class)
class EvaluationAttemptControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var evaluationService: EvaluationService

    @Test
    fun `답변 제출은 원문과 정확한 열 필드의 JSON 및 생성된 기록 Location을 반환한다`() {
        // given
        val questionId = 10L
        val answer = "  첫 줄\n둘째 줄\t\"인용\"과 \\ 경로  "
        val attempt = attemptResponse(questionId, answer)
        given(evaluationService.submit(questionId, answer)).willReturn(attempt)
        val request = post("/api/questions/{questionId}/evaluation-attempts", questionId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("answer" to answer)))

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertCreatedResponse(response, attempt)
        verify(evaluationService).submit(questionId, answer)
        verifyNoMoreInteractions(evaluationService)
    }

    @ParameterizedTest
    @ValueSource(longs = [1L, 9_007_199_254_740_991L])
    fun `질문 ID 범위의 최솟값과 최댓값은 답변 제출을 허용한다`(questionId: Long) {
        // given
        val answer = "경계값 답변"
        val attempt = attemptResponse(questionId, answer)
        given(evaluationService.submit(questionId, answer)).willReturn(attempt)
        val request = post("/api/questions/{questionId}/evaluation-attempts", questionId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("answer" to answer)))

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertCreatedResponse(response, attempt)
        verify(evaluationService).submit(questionId, answer)
        verifyNoMoreInteractions(evaluationService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "9007199254740992", "abc", "1.5", "9223372036854775808"])
    fun `잘못된 질문 ID는 서비스 호출 없이 요청 오류를 반환한다`(questionId: String) {
        // given
        val request = post("/api/questions/{questionId}/evaluation-attempts", questionId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"answer":"유효한 답변"}""")

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertErrorResponse(response, 400, "INVALID_REQUEST", "요청 값의 형식이나 범위가 올바르지 않습니다.")
        verifyNoInteractions(evaluationService)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "{}",
        """{"answer":null}""",
        """{"answer":""}""",
        """{"answer":"   "}""",
        """{"answer":"\t\r\n"}""",
        """{"answer":123}""",
        """{"answer":1.5}""",
        """{"answer":true}""",
        """{"answer":false}""",
        """{"answer":[]}""",
        """{"answer":["답변"]}""",
        """{"answer":{}}""",
        """{"answer":{"text":"답변"}}""",
        """{"answer":"닫히지 않은 본문""",
        "[]",
        "null",
        "",
    ])
    fun `누락되거나 비어 있거나 문자열이 아닌 답변과 잘못된 본문은 서비스 호출 없이 거부한다`(content: String) {
        // given
        val request = post("/api/questions/{questionId}/evaluation-attempts", 10L)
            .contentType(MediaType.APPLICATION_JSON)
            .content(content)

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertErrorResponse(response, 400, "INVALID_REQUEST", "요청 값의 형식이나 범위가 올바르지 않습니다.")
        verifyNoInteractions(evaluationService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "\uD83D\uDE00"])
    fun `일반 문자와 이모지 답변은 삼천 UTF16 코드 단위까지 원문 그대로 허용한다`(character: String) {
        // given
        val questionId = 10L
        val answer = character.repeat(3_000 / character.length)
        val attempt = attemptResponse(questionId, answer)
        given(evaluationService.submit(questionId, answer)).willReturn(attempt)
        val request = post("/api/questions/{questionId}/evaluation-attempts", questionId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("answer" to answer)))

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(answer.length).isEqualTo(3_000)
        assertCreatedResponse(response, attempt)
        verify(evaluationService).submit(questionId, answer)
        verifyNoMoreInteractions(evaluationService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "\uD83D\uDE00"])
    fun `일반 문자와 이모지 답변은 삼천일 UTF16 코드 단위부터 서비스 호출 없이 거부한다`(character: String) {
        // given
        val answer = character.repeat(3_000 / character.length) + "a"
        val request = post("/api/questions/{questionId}/evaluation-attempts", 10L)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("answer" to answer)))

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(answer.length).isEqualTo(3_001)
        assertErrorResponse(response, 400, "INVALID_REQUEST", "요청 값의 형식이나 범위가 올바르지 않습니다.")
        verifyNoInteractions(evaluationService)
    }

    @Test
    fun `답변의 앞뒤 공백과 줄바꿈도 최대 길이에 포함하여 서비스 호출 없이 거부한다`() {
        // given
        val answer = " " + "a".repeat(2_999) + "\n"
        val request = post("/api/questions/{questionId}/evaluation-attempts", 10L)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("answer" to answer)))

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(answer.length).isEqualTo(3_001)
        assertErrorResponse(response, 400, "INVALID_REQUEST", "요청 값의 형식이나 범위가 올바르지 않습니다.")
        verifyNoInteractions(evaluationService)
    }

    @Test
    fun `존재하지 않는 질문에 답변을 제출하면 Location 없이 안전한 질문 없음 오류를 반환한다`() {
        // given
        val questionId = 10L
        val answer = "답변"
        given(evaluationService.submit(questionId, answer)).willThrow(QuestionNotFoundException())
        val request = post("/api/questions/{questionId}/evaluation-attempts", questionId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("answer" to answer)))

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertErrorResponse(response, 404, "QUESTION_NOT_FOUND", "질문을 찾을 수 없습니다.")
        verify(evaluationService).submit(questionId, answer)
        verifyNoMoreInteractions(evaluationService)
    }

    @Test
    fun `평가 실패는 Location 없이 안전한 평가 실패 코드와 메시지를 반환한다`() {
        // given
        val questionId = 10L
        val answer = "답변"
        given(evaluationService.submit(questionId, answer)).willThrow(LlmEvaluationFailedException())
        val request = post("/api/questions/{questionId}/evaluation-attempts", questionId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("answer" to answer)))

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertErrorResponse(response, 502, "LLM_EVALUATION_FAILED", "평가 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
        verify(evaluationService).submit(questionId, answer)
        verifyNoMoreInteractions(evaluationService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["database", "unexpected"])
    fun `DB 오류와 예상하지 못한 오류는 내부 정보와 Location 없이 서버 오류를 반환한다`(errorType: String) {
        // given
        val questionId = 10L
        val answer = "답변"
        val internalMessage = "SQL evaluation_attempt answer=private-answer api-key=secret-key private-criterion"
        val exception = if (errorType == "database") {
            DataIntegrityViolationException(internalMessage)
        } else {
            IllegalStateException(internalMessage)
        }
        given(evaluationService.submit(questionId, answer)).willThrow(exception)
        val request = post("/api/questions/{questionId}/evaluation-attempts", questionId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("answer" to answer)))

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertErrorResponse(response, 500, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.")
        assertThat(response.contentAsString).doesNotContain(internalMessage, "private-answer", "secret-key", "private-criterion")
        verify(evaluationService).submit(questionId, answer)
        verifyNoMoreInteractions(evaluationService)
    }

    @ParameterizedTest
    @EnumSource(EvaluationResult::class)
    fun `평가 결과 조회는 모든 판정에서 제출과 동일한 열 필드 및 저장된 원문과 UTC 시각을 반환한다`(result: EvaluationResult) {
        // given
        val score = when (result) {
            EvaluationResult.FAIL -> 30
            EvaluationResult.RETRY -> 60
            EvaluationResult.PASS -> 85
        }
        val answer = "  첫 줄\n둘째 줄\t\"인용\"과 \\ 경로 \uD83D\uDE00  "
        val attempt = attemptResponse(10L, answer).copy(score = score, result = result)
        given(evaluationService.getAttempt(attempt.id)).willReturn(attempt)
        val request = get("/api/evaluation-attempts/{attemptId}", attempt.id)

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isNull()
        assertResponseBody(response, attempt)
        verify(evaluationService).getAttempt(attempt.id)
        verifyNoMoreInteractions(evaluationService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "9007199254740992", "abc", "1.5", "9223372036854775808"])
    fun `잘못된 평가 기록 ID 조회는 서비스 호출 없이 요청 오류를 반환한다`(attemptId: String) {
        // given
        val request = get("/api/evaluation-attempts/{attemptId}", attemptId)

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertErrorResponse(response, 400, "INVALID_REQUEST", "요청 값의 형식이나 범위가 올바르지 않습니다.")
        verifyNoInteractions(evaluationService)
    }

    @ParameterizedTest
    @ValueSource(longs = [1L, 9_007_199_254_740_991L])
    fun `평가 기록 ID 범위의 최솟값과 최댓값은 결과 조회를 허용한다`(attemptId: Long) {
        // given
        val attempt = attemptResponse(10L, "경계값 답변").copy(id = attemptId)
        given(evaluationService.getAttempt(attemptId)).willReturn(attempt)
        val request = get("/api/evaluation-attempts/{attemptId}", attemptId)

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isNull()
        assertResponseBody(response, attempt)
        verify(evaluationService).getAttempt(attemptId)
        verifyNoMoreInteractions(evaluationService)
    }

    @Test
    fun `존재하지 않는 평가 기록 조회는 Location 없이 안전한 평가 결과 없음 오류를 반환한다`() {
        // given
        val attemptId = 42L
        given(evaluationService.getAttempt(attemptId)).willThrow(EvaluationAttemptNotFoundException())
        val request = get("/api/evaluation-attempts/{attemptId}", attemptId)

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertErrorResponse(response, 404, "EVALUATION_ATTEMPT_NOT_FOUND", "평가 결과를 찾을 수 없습니다.")
        verify(evaluationService).getAttempt(attemptId)
        verifyNoMoreInteractions(evaluationService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["database", "missing-question"])
    fun `평가 기록 조회의 DB 오류와 참조 질문 누락은 내부 정보와 Location 없는 서버 오류를 반환한다`(errorType: String) {
        // given
        val attemptId = 42L
        val internalMessage = "SQL evaluation_attempt question=private-question answer=private-answer api-key=secret-key private-criterion"
        val exception = if (errorType == "database") {
            DataIntegrityViolationException(internalMessage)
        } else {
            IllegalStateException(internalMessage)
        }
        given(evaluationService.getAttempt(attemptId)).willThrow(exception)
        val request = get("/api/evaluation-attempts/{attemptId}", attemptId)

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertErrorResponse(response, 500, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.")
        assertThat(response.contentAsString).doesNotContain(
            internalMessage, "evaluation_attempt", "private-question", "private-answer", "secret-key", "private-criterion",
        )
        verify(evaluationService).getAttempt(attemptId)
        verifyNoMoreInteractions(evaluationService)
    }

    private fun attemptResponse(questionId: Long, answer: String) = EvaluationAttemptResponse(
        id = 42L,
        questionId = questionId,
        questionTitle = "트랜잭션이란 무엇인가요?",
        answer = answer,
        score = 85,
        result = EvaluationResult.PASS,
        strengths = "  원자성을 설명했습니다.\n예시가 적절합니다.  ",
        weaknesses = "격리 수준 설명이 부족합니다.",
        improvements = "격리 수준을 비교해보세요.",
        createdAt = Instant.parse("2026-10-05T04:00:00.123456Z"),
    )

    private fun assertCreatedResponse(response: MockHttpServletResponse, attempt: EvaluationAttemptResponse) {
        assertThat(response.status).isEqualTo(201)
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/evaluation-attempts/${attempt.id}")
        assertResponseBody(response, attempt)
    }

    private fun assertResponseBody(response: MockHttpServletResponse, attempt: EvaluationAttemptResponse) {
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType))).isEqualTo(MediaType.APPLICATION_JSON)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body.isObject).isTrue()
        assertThat(body.propertyNames()).containsExactlyInAnyOrder(
            "id", "questionId", "questionTitle", "answer", "score", "result",
            "strengths", "weaknesses", "improvements", "createdAt",
        )
        assertThat(body.get("id").isIntegralNumber).isTrue()
        assertThat(body.get("id").asLong()).isEqualTo(attempt.id)
        assertThat(body.get("questionId").isIntegralNumber).isTrue()
        assertThat(body.get("questionId").asLong()).isEqualTo(attempt.questionId)
        assertThat(body.get("score").isIntegralNumber).isTrue()
        assertThat(body.get("score").asInt()).isEqualTo(attempt.score)
        val stringFields = mapOf(
            "questionTitle" to attempt.questionTitle,
            "answer" to attempt.answer,
            "result" to attempt.result.name,
            "strengths" to attempt.strengths,
            "weaknesses" to attempt.weaknesses,
            "improvements" to attempt.improvements,
            "createdAt" to attempt.createdAt.toString(),
        )
        assertThat(stringFields.entries).allSatisfy { (field, expected) ->
            assertThat(body.get(field).isString).isTrue()
            assertThat(body.get(field).asString()).isEqualTo(expected)
        }
        assertThat(Instant.parse(body.get("createdAt").asString())).isEqualTo(attempt.createdAt)
    }

    private fun assertErrorResponse(response: MockHttpServletResponse, status: Int, code: String, message: String) {
        assertThat(response.status).isEqualTo(status)
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isNull()
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType))).isEqualTo(MediaType.APPLICATION_JSON)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body.isObject).isTrue()
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message")
        assertThat(body.get("code").isString).isTrue()
        assertThat(body.get("code").asString()).isEqualTo(code)
        assertThat(body.get("message").isString).isTrue()
        assertThat(body.get("message").asString()).isEqualTo(message)
    }
}
