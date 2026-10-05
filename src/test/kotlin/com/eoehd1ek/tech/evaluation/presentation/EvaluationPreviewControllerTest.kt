package com.eoehd1ek.tech.evaluation.presentation

import com.eoehd1ek.tech.evaluation.application.EvaluationService
import com.eoehd1ek.tech.evaluation.application.exception.LlmEvaluationFailedException
import com.eoehd1ek.tech.evaluation.domain.EvaluationResult
import com.eoehd1ek.tech.evaluation.presentation.request.EvaluationPreviewRequest
import com.eoehd1ek.tech.evaluation.presentation.response.EvaluationPreviewResponse
import com.eoehd1ek.tech.question.presentation.request.AdminCriterionRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper

@WebMvcTest(EvaluationPreviewController::class)
class EvaluationPreviewControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @MockitoBean
    private lateinit var service: EvaluationService

    @Test
    fun `저장 전 평가 요청은 저장 ID 없이 총점 판정과 종합 피드백을 반환한다`() {
        // given
        val request = EvaluationPreviewRequest(
            "제목", "본문", listOf(AdminCriterionRequest("기준", 100, 0)),
            "  답변\n원문  "
        )
        val expected = EvaluationPreviewResponse(
            request.title, request.answer, 85, EvaluationResult.PASS,
            "장점", "단점", "개선점"
        )
        given(service.preview(request)).willReturn(expected)

        // when
        val response = mockMvc.perform(post("/api/admin/questions/evaluation-preview")
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request))).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(response.getHeader("Location")).isNull()
        val body = mapper.readTree(response.contentAsByteArray)
        assertThat(body).isEqualTo(mapper.readTree(mapper.writeValueAsBytes(expected)))
        assertThat(body.propertyNames()).containsExactlyInAnyOrder(
            "questionTitle", "answer", "score", "result", "strengths", "weaknesses", "improvements",
        )
        verify(service).preview(request)
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    fun `잘못된 preview 입력은 평가 호출 전에 거부한다`(body: String) {
        // given
        val request = post("/api/admin/questions/evaluation-preview")
            .contentType(MediaType.APPLICATION_JSON).content(body)

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(400)
        assertThat(mapper.readTree(response.contentAsByteArray).get("code").asString()).isEqualTo("INVALID_REQUEST")
        verifyNoInteractions(service)
    }

    @Test
    fun `preview 답변은 기본 Jackson 변환과 최대 길이를 허용한다`() {
        // given
        val criterion = listOf(AdminCriterionRequest("기준", 100, 1))
        val requests = listOf(
            EvaluationPreviewRequest("제목", "본문", criterion, "123"),
            EvaluationPreviewRequest("제목", "본문", criterion, "가".repeat(3000)),
        )
        requests.forEach {
            given(service.preview(it)).willReturn(
                EvaluationPreviewResponse(
                    it.title, it.answer, 80, EvaluationResult.PASS, "장점", "단점", "개선점",
                )
            )
        }
        val bodies = requests.mapIndexed { index, item ->
            if (index == 0) mapper.writeValueAsString(mapOf(
                "title" to item.title, "content" to item.content, "criteria" to criterion, "answer" to 123,
            )) else mapper.writeValueAsString(item)
        }

        // when
        val responses = bodies.map {
            mockMvc.perform(post("/api/admin/questions/evaluation-preview")
                .contentType(MediaType.APPLICATION_JSON).content(it)).andReturn().response
        }

        // then
        assertThat(responses.map { it.status }).containsExactly(200, 200)
        requests.forEach { verify(service).preview(it) }
    }

    @Test
    fun `preview 평가 실패는 기존 공통 평가 오류를 반환한다`() {
        // given
        val request = EvaluationPreviewRequest("제목", "본문", listOf(AdminCriterionRequest("기준", 100, 1)), "답변")
        given(service.preview(request)).willThrow(LlmEvaluationFailedException())

        // when
        val response = mockMvc.perform(post("/api/admin/questions/evaluation-preview")
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request))).andReturn().response

        // then
        assertThat(response.status).isEqualTo(502)
        assertThat(mapper.readTree(response.contentAsByteArray).get("code").asString())
            .isEqualTo("LLM_EVALUATION_FAILED")
    }

    companion object {
        @JvmStatic
        fun invalidBodies(): List<String> {
            val mapper = ObjectMapper()
            val criterion = mapOf("content" to "기준", "maxScore" to 100, "displayOrder" to 1)
            val valid = mapOf("title" to "제목", "content" to "본문", "criteria" to listOf(criterion), "answer" to "답변")
            return listOf(
                valid - "answer",
                valid + ("answer" to null),
                valid + ("answer" to " "),
                valid + ("answer" to "가".repeat(3001)),
                valid + ("title" to "가".repeat(201)),
                valid + ("content" to "가".repeat(10001)),
                valid + ("criteria" to emptyList<Any>()),
                valid + ("criteria" to List(11) { criterion }),
                valid + ("criteria" to listOf(criterion + ("content" to "가".repeat(1001)))),
                valid + ("criteria" to listOf(criterion + ("maxScore" to 99))),
                valid + ("criteria" to listOf(criterion - "displayOrder")),
                valid + ("criteria" to listOf(criterion + ("displayOrder" to null))),
                valid + ("criteria" to listOf(criterion + ("maxScore" to 50), criterion + ("maxScore" to 50))),
            ).map(mapper::writeValueAsString)
        }
    }
}
