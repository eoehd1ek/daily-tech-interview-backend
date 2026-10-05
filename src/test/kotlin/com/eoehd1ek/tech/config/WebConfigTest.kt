package com.eoehd1ek.tech.config

import com.eoehd1ek.tech.question.QuestionController
import com.eoehd1ek.tech.question.QuestionNotFoundException
import com.eoehd1ek.tech.question.QuestionResponse
import com.eoehd1ek.tech.question.QuestionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options

@WebMvcTest(
    controllers = [QuestionController::class],
    properties = [
        "app.cors.allowed-origins[0]=http://localhost:5173",
        "app.cors.allowed-origins[1]=https://frontend.example.com",
    ],
)
@Import(WebConfig::class)
class WebConfigTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var questionService: QuestionService

    @ParameterizedTest
    @ValueSource(strings = ["http://localhost:5173", "https://frontend.example.com"])
    fun `설정된 Origin의 요청은 해당 Origin과 공개 헤더를 반환한다`(origin: String) {
        // given
        given(questionService.getQuestions()).willReturn(listOf(QuestionResponse(10L, "테스트 질문")))

        // when
        val response = mockMvc.perform(get("/api/questions").header(HttpHeaders.ORIGIN, origin))
            .andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(origin)
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)).isEqualTo("Location")
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["POST", "PUT"])
    fun `허용 Origin의 JSON 쓰기 사전 요청은 Service 호출 없이 처리한다`(method: String) {
        // given
        val origin = "http://localhost:5173"
        val request = options("/api/questions")
            .header(HttpHeaders.ORIGIN, origin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(origin)
        val allowedMethods = response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
            ?.split(",")
            ?.map(String::trim)
        assertThat(allowedMethods)
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "OPTIONS")
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)).isEqualTo("Content-Type")
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull()
        verifyNoInteractions(questionService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["https://untrusted.example.com", "http://127.0.0.1:5173", "http://localhost:5174"])
    fun `등록되지 않은 Origin의 요청은 Service 호출 없이 거부한다`(origin: String) {
        // given
        val request = get("/api/questions").header(HttpHeaders.ORIGIN, origin)

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(403)
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull()
        verifyNoInteractions(questionService)
    }

    @Test
    fun `허용하지 않은 메서드의 사전 요청은 거부한다`() {
        // given
        val request = options("/api/questions")
            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE")

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(403)
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull()
        verifyNoInteractions(questionService)
    }

    @Test
    fun `허용하지 않은 헤더의 사전 요청은 거부한다`() {
        // given
        val request = options("/api/questions")
            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(403)
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull()
        verifyNoInteractions(questionService)
    }

    @Test
    fun `허용 Origin에서 발생한 404 응답에도 CORS 허용 헤더를 포함한다`() {
        // given
        val origin = "http://localhost:5173"
        given(questionService.getQuestion(10L)).willThrow(QuestionNotFoundException())

        // when
        val response = mockMvc.perform(get("/api/questions/10").header(HttpHeaders.ORIGIN, origin))
            .andReturn().response

        // then
        assertThat(response.status).isEqualTo(404)
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(origin)
    }

    @Test
    fun `Origin이 없는 요청은 기존 API 동작을 유지한다`() {
        // given
        given(questionService.getQuestions()).willReturn(emptyList())

        // when
        val response = mockMvc.perform(get("/api/questions")).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(response.contentAsString).isEqualTo("[]")
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull()
    }
}
