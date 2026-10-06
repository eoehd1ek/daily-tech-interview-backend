package com.eoehd1ek.tech.question.presentation

import com.eoehd1ek.tech.config.SecurityConfig
import com.eoehd1ek.tech.question.application.QuestionService
import com.eoehd1ek.tech.question.application.exception.QuestionNotFoundException
import com.eoehd1ek.tech.question.presentation.response.QuestionDetailResponse
import com.eoehd1ek.tech.question.presentation.response.QuestionResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import tools.jackson.databind.ObjectMapper

@WebMvcTest(QuestionController::class)
@Import(SecurityConfig::class)
class QuestionControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var questionService: QuestionService

    @MockitoBean
    private lateinit var userDetailsService: UserDetailsService

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

    @Test
    fun `질문 상세 요청은 ID와 제목과 본문만 포함한 JSON 객체를 반환한다`() {
        // given
        val question = QuestionDetailResponse(10L, "상세 질문", "상세 질문 본문")
        given(questionService.getQuestion(question.id)).willReturn(question)

        // when
        val response = mockMvc.perform(get("/api/questions/{questionId}", question.id)).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType)))
            .isEqualTo(MediaType.APPLICATION_JSON)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body.isObject).isTrue()
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("id", "title", "content")
        assertThat(body.get("id").isIntegralNumber).isTrue()
        assertThat(body.get("id").asLong()).isEqualTo(question.id)
        assertThat(body.get("title").isString).isTrue()
        assertThat(body.get("title").asString()).isEqualTo(question.title)
        assertThat(body.get("content").isString).isTrue()
        assertThat(body.get("content").asString()).isEqualTo(question.content)
        verify(questionService).getQuestion(question.id)
    }

    @Test
    fun `존재하지 않는 질문 요청은 질문 없음 오류를 반환한다`() {
        // given
        val questionId = 10L
        given(questionService.getQuestion(questionId)).willThrow(QuestionNotFoundException())

        // when
        val response = mockMvc.perform(get("/api/questions/{questionId}", questionId)).andReturn().response

        // then
        assertThat(response.status).isEqualTo(404)
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType)))
            .isEqualTo(MediaType.APPLICATION_JSON)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body.isObject).isTrue()
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message")
        assertThat(body.get("code").asString()).isEqualTo("QUESTION_NOT_FOUND")
        assertThat(body.get("message").isString).isTrue()
        assertThat(body.get("message").asString()).isNotBlank()
        verify(questionService).getQuestion(questionId)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "9007199254740992", "abc", "1.5", "9223372036854775808"])
    fun `잘못된 질문 ID 요청은 Service 호출 없이 요청 오류를 반환한다`(questionId: String) {
        // given
        val request = get("/api/questions/{questionId}", questionId)

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(400)
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType)))
            .isEqualTo(MediaType.APPLICATION_JSON)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body.isObject).isTrue()
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message")
        assertThat(body.get("code").asString()).isEqualTo("INVALID_REQUEST")
        assertThat(body.get("message").isString).isTrue()
        assertThat(body.get("message").asString()).isNotBlank()
        verifyNoInteractions(questionService)
    }

    @ParameterizedTest
    @ValueSource(longs = [1L, 9_007_199_254_740_991L])
    fun `지원 ID 범위의 최솟값과 최댓값은 상세 조회를 허용한다`(questionId: Long) {
        // given
        val question = QuestionDetailResponse(questionId, "경계값 질문", "질문 본문")
        given(questionService.getQuestion(questionId)).willReturn(question)

        // when
        val response = mockMvc.perform(get("/api/questions/{questionId}", questionId)).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body.get("id").asLong()).isEqualTo(questionId)
        verify(questionService).getQuestion(questionId)
    }
}
