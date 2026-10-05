package com.eoehd1ek.tech.common

import com.eoehd1ek.tech.evaluation.LlmEvaluationFailedException
import jakarta.validation.constraints.Min
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

@WebMvcTest(GlobalExceptionHandlerTest.TestController::class)
@Import(GlobalExceptionHandlerTest.TestController::class)
class GlobalExceptionHandlerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `질문 외 Controller의 비즈니스 예외도 정의한 상태와 코드로 반환한다`() {
        // given
        val request = get("/test/errors/business")

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(409)
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType)))
            .isEqualTo(MediaType.APPLICATION_JSON)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message")
        assertThat(body.get("code").asString()).isEqualTo("TEST_BUSINESS_ERROR")
        assertThat(body.get("message").asString()).isEqualTo("테스트 비즈니스 오류입니다.")
    }

    @ParameterizedTest
    @ValueSource(strings = ["abc", "0"])
    fun `질문 외 Controller의 입력 타입과 범위 오류도 요청 오류로 반환한다`(id: String) {
        // given
        val request = get("/test/errors/input/{id}", id)

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(400)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message")
        assertThat(body.get("code").asString()).isEqualTo("INVALID_REQUEST")
        assertThat(body.get("message").asString()).isEqualTo("요청 값의 형식이나 범위가 올바르지 않습니다.")
    }

    @Test
    fun `Controller 반환값 검증 실패는 요청 오류가 아닌 서버 오류로 반환한다`() {
        // given
        val request = get("/test/errors/return-value")

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(500)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message")
        assertThat(body.get("code").asString()).isEqualTo("INTERNAL_SERVER_ERROR")
        assertThat(body.get("message").asString()).isEqualTo("서버 내부 오류가 발생했습니다.")
    }

    @Test
    fun `평가 실패 예외는 내부 정보 없이 502 평가 실패 응답을 반환한다`() {
        // given
        val request = get("/test/errors/evaluation")

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(502)
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType)))
            .isEqualTo(MediaType.APPLICATION_JSON)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message")
        assertThat(body.get("code").asString()).isEqualTo("LLM_EVALUATION_FAILED")
        assertThat(body.get("message").asString()).isEqualTo("평가 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
    }

    @RestController
    class TestController {
        @GetMapping("/test/errors/business")
        fun businessError(): String = throw TestBusinessException()

        @GetMapping("/test/errors/input/{id}")
        fun input(@PathVariable("id") @Min(1) id: Long): Long = id

        @GetMapping("/test/errors/return-value")
        @Min(1)
        fun invalidReturnValue(): Long = 0

        @GetMapping("/test/errors/evaluation")
        fun evaluationError(): String = throw LlmEvaluationFailedException()
    }

    private class TestBusinessException : BusinessException(
        status = HttpStatus.CONFLICT,
        code = "TEST_BUSINESS_ERROR",
        message = "테스트 비즈니스 오류입니다.",
    )
}
