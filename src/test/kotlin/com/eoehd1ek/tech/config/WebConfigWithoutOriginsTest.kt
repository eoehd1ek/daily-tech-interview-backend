package com.eoehd1ek.tech.config

import com.eoehd1ek.tech.question.QuestionController
import com.eoehd1ek.tech.question.QuestionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

@WebMvcTest(
    controllers = [QuestionController::class],
)
@Import(WebConfig::class)
class WebConfigWithoutOriginsTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var questionService: QuestionService

    @Test
    fun `허용 Origin 설정이 없으면 교차 Origin 요청을 Service 호출 없이 거부한다`() {
        // given
        val request = get("/api/questions").header(HttpHeaders.ORIGIN, "http://localhost:5173")

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(403)
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull()
        verifyNoInteractions(questionService)
    }
}
