package com.eoehd1ek.tech.question

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import tools.jackson.databind.ObjectMapper

@WebMvcTest(AdminQuestionController::class)
class AdminQuestionControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var service: AdminQuestionService

    @Test
    fun `질문 생성은 인증 없이 저장된 상세와 생성 상태 및 Location을 반환한다`() {
        // given
        val request = AdminQuestionRequest(" 제목 ", "본문\n원문", listOf(AdminCriterionRequest("기준", 100, 1)))
        val expected = AdminQuestionResponse(10L, request.title, request.content,
            listOf(AdminCriterionResponse(20L, "기준", 100, 1)))
        given(service.create(request)).willReturn(expected)

        // when
        val response = mockMvc.perform(post("/api/admin/questions")
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
            .andReturn().response

        // then
        assertThat(response.status).isEqualTo(201)
        assertThat(response.getHeader("Location")).isEqualTo("/api/admin/questions/10")
        assertThat(objectMapper.readTree(response.contentAsByteArray))
            .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsBytes(expected)))
        verify(service).create(request)
    }

    @Test
    fun `질문 수정은 입력 상한을 허용하고 수정된 상세를 반환한다`() {
        // given
        val request = AdminQuestionRequest("가".repeat(200), "나".repeat(10000),
            List(10) { AdminCriterionRequest("다".repeat(1000), 10, it + 1) })
        val expected = AdminQuestionResponse(10L, request.title, request.content,
            request.criteria.mapIndexed { index, item ->
                AdminCriterionResponse(20L + index, requireNotNull(item).content, item.maxScore, index + 1)
            })
        given(service.update(10L, request)).willReturn(expected)

        // when
        val response = mockMvc.perform(put("/api/admin/questions/10")
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
            .andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(response.getHeader("Location")).isNull()
        assertThat(objectMapper.readTree(response.contentAsByteArray))
            .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsBytes(expected)))
        verify(service).update(10L, request)
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    fun `잘못된 질문 입력은 생성과 수정 모두 Service 호출 없이 거부한다`(body: String) {
        // given
        val requests = listOf(post("/api/admin/questions"), put("/api/admin/questions/10"))

        // when
        val responses = requests.map {
            mockMvc.perform(it.contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().response
        }

        // then
        responses.forEach {
            assertThat(it.status).isEqualTo(400)
            val json = objectMapper.readTree(it.contentAsByteArray)
            assertThat(json.propertyNames()).containsExactlyInAnyOrder("code", "message")
            assertThat(json.get("code").asString()).isEqualTo("INVALID_REQUEST")
        }
        verifyNoInteractions(service)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "9007199254740992", "abc"])
    fun `잘못된 수정 ID는 Service 호출 없이 요청 오류를 반환한다`(id: String) {
        // given
        val body = """{"title":"제목","content":"본문","criteria":[{"content":"기준","maxScore":100,"displayOrder":1}]}"""

        // when
        val response = mockMvc.perform(put("/api/admin/questions/$id")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().response

        // then
        assertThat(response.status).isEqualTo(400)
        assertThat(objectMapper.readTree(response.contentAsByteArray).get("code").asString())
            .isEqualTo("INVALID_REQUEST")
        verifyNoInteractions(service)
    }

    @Test
    fun `존재하지 않는 질문 수정은 질문 없음 오류를 반환한다`() {
        // given
        val request = AdminQuestionRequest("제목", "본문", listOf(AdminCriterionRequest("기준", 100, 1)))
        given(service.update(10L, request)).willThrow(QuestionNotFoundException())

        // when
        val response = mockMvc.perform(put("/api/admin/questions/10")
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
            .andReturn().response

        // then
        assertThat(response.status).isEqualTo(404)
        assertThat(objectMapper.readTree(response.contentAsByteArray).get("code").asString())
            .isEqualTo("QUESTION_NOT_FOUND")
    }

    @Test
    fun `저장 실패는 안전한 서버 오류를 반환한다`() {
        // given
        val request = AdminQuestionRequest("제목", "본문", listOf(AdminCriterionRequest("기준", 100, 1)))
        given(service.create(request)).willThrow(IllegalStateException("private database details"))

        // when
        val response = mockMvc.perform(post("/api/admin/questions")
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
            .andReturn().response

        // then
        assertThat(response.status).isEqualTo(500)
        assertThat(objectMapper.readTree(response.contentAsByteArray).get("code").asString())
            .isEqualTo("INTERNAL_SERVER_ERROR")
        assertThat(response.contentAsString).doesNotContain("private database details")
    }

    companion object {
        @JvmStatic
        fun invalidBodies(): List<String> {
            val mapper = ObjectMapper()
            val criterion = mapOf("content" to "기준", "maxScore" to 100, "displayOrder" to 1)
            val valid = mapOf("title" to "제목", "content" to "본문", "criteria" to listOf(criterion))
            val bodies = mutableListOf<Map<String, Any?>>()
            for ((field, limit) in listOf("title" to 200, "content" to 10000)) {
                bodies += valid - field
                for (value in listOf(null, " ", emptyMap<String, String>(), "가".repeat(limit + 1))) {
                    bodies += valid + (field to value)
                }
            }
            bodies += valid - "criteria"
            for (value in listOf(null, "invalid", emptyList<Any>(), listOf(null), List(11) { criterion })) {
                bodies += valid + ("criteria" to value)
            }
            bodies += valid + ("criteria" to listOf(criterion - "content"))
            for (value in listOf(null, "", " ", emptyMap<String, String>(), "가".repeat(1001))) {
                bodies += valid + ("criteria" to listOf(criterion + ("content" to value)))
            }
            bodies += valid + ("criteria" to listOf(criterion - "maxScore"))
            for (value in listOf(null, 0, -1, 99, 101, "invalid", true)) {
                bodies += valid + ("criteria" to listOf(criterion + ("maxScore" to value)))
            }
            bodies += valid + ("criteria" to listOf(criterion, criterion))
            bodies += valid + ("criteria" to listOf(criterion - "displayOrder"))
            bodies += valid + ("criteria" to listOf(criterion + ("displayOrder" to null)))
            bodies += valid + ("criteria" to listOf(
                criterion + ("maxScore" to 50), criterion + ("maxScore" to 50),
            ))
            return bodies.map(mapper::writeValueAsString)
        }
    }
}
