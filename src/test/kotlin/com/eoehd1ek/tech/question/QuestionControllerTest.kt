package com.eoehd1ek.tech.question

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import tools.jackson.databind.ObjectMapper

@WebMvcTest(QuestionController::class)
class QuestionControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var questionService: QuestionService

    @Test
    fun `질문 목록 요청은 ID와 제목만 포함한 JSON 배열을 반환한다`() {
        // given
        val questions = listOf(
            QuestionResponse(10L, "첫 번째 질문"),
            QuestionResponse(20L, "두 번째 질문"),
        )
        given(questionService.getQuestions()).willReturn(questions)

        // when
        val response = mockMvc.perform(get("/api/questions"))
            .andReturn()
            .response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType))).isEqualTo(MediaType.APPLICATION_JSON)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body.isArray).isTrue()
        assertThat(body.size()).isEqualTo(questions.size)
        assertThat(
            body.toList()
                .map { it.get("id").asLong() to it.get("title").asString() }).containsExactlyInAnyOrderElementsOf(
            questions.map { it.id to it.title })
        assertThat(body.toList()).allSatisfy { item ->
            assertThat(item.properties().map { it.key }).containsExactlyInAnyOrder("id", "title")
            assertThat(item.get("id").isIntegralNumber).isTrue()
            assertThat(item.get("title").isString).isTrue()
        }
        verify(questionService).getQuestions()
    }

    @Test
    fun `질문이 없으면 성공 상태와 빈 JSON 배열을 반환한다`() {
        // given
        given(questionService.getQuestions()).willReturn(emptyList())

        // when
        val response = mockMvc.perform(get("/api/questions")).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType)))
            .isEqualTo(MediaType.APPLICATION_JSON)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body.isArray).isTrue()
        assertThat(body.toList()).isEmpty()
        verify(questionService).getQuestions()
    }
}
