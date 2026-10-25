package com.eoehd1ek.tech.llmcalllog.presentation

import com.eoehd1ek.tech.config.SecurityConfig
import com.eoehd1ek.tech.llmcalllog.application.LlmCallLogService
import com.eoehd1ek.tech.llmcalllog.application.exception.LlmCallLogNotFoundException
import com.eoehd1ek.tech.llmcalllog.application.model.LlmCallLogFilter
import com.eoehd1ek.tech.llmcalllog.application.result.LlmCallLogPage
import com.eoehd1ek.tech.llmcalllog.application.result.LlmCallLogSummary
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallFailureStage
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallLog
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallPurpose
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallStatus
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
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@WebMvcTest(LlmCallLogController::class)
@Import(SecurityConfig::class)
class LlmCallLogControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @MockitoBean
    private lateinit var service: LlmCallLogService

    @MockitoBean
    private lateinit var userDetailsService: UserDetailsService

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `관리자 기본 목록은 요약과 다섯 페이지 필드만 반환한다`() {
        // given
        val log = log()
        val item = LlmCallLogSummary.from(log)
        given(service.getLogs(LlmCallLogFilter())).willReturn(LlmCallLogPage(listOf(item), 0, 20, 1, 1))

        // when
        val response = mockMvc.perform(get("/api/admin/llm-call-logs")).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType))).isEqualTo(MediaType.APPLICATION_JSON)
        val body = mapper.readTree(response.contentAsByteArray)
        assertThat(body.propertyNames()).containsExactlyInAnyOrder(
            "items",
            "page",
            "size",
            "totalElements",
            "totalPages"
        )
        assertThat(body.get("items").get(0).propertyNames()).containsExactlyInAnyOrder(
            "id", "purpose", "status", "provider", "requestedModel", "responseModel", "startedAt",
            "completedAt", "durationMs", "inputTokens", "outputTokens", "totalTokens", "failureStage"
        )
        assertThat(body.get("items").get(0).get("responseModel").isNull).isTrue()
        assertThat(response.contentAsString).doesNotContain("private-answer", "requestMessages", "errorData")
        verify(service).getLogs(LlmCallLogFilter())
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `유효한 최대 페이지와 필터는 UTC 시각으로 변환하고 빈 페이지를 반환한다`() {
        // given
        val from = Instant.parse("2026-10-25T00:00:00Z")
        val to = from.plusSeconds(86400)
        val filter = LlmCallLogFilter(
            2_147_483_646, 100, LlmCallStatus.FAILED,
            LlmCallPurpose.ADMIN_PREVIEW, "가".repeat(255), from, to
        )
        given(service.getLogs(filter)).willReturn(LlmCallLogPage(emptyList(), filter.page, 100, 21, 1))

        // when
        val response = mockMvc.perform(
            get("/api/admin/llm-call-logs")
                .param("page", "2147483646").param("size", "100").param("status", "FAILED")
                .param("purpose", "ADMIN_PREVIEW").param("model", filter.model!!)
                .param("startedFrom", "2026-10-25T09:00:00+09:00")
                .param("startedTo", "2026-10-26T00:00:00Z")
        ).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        val body = mapper.readTree(response.contentAsByteArray)
        assertThat(body.get("items").isArray).isTrue()
        assertThat(body.get("items").size()).isZero()
        assertThat(body.get("page").asInt()).isEqualTo(2_147_483_646)
        assertThat(body.get("totalElements").asLong()).isEqualTo(21)
        verify(service).getLogs(filter)
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    @WithMockUser(roles = ["ADMIN"])
    fun `잘못된 페이지 필터와 빈 파라미터는 원문 없는 안전한 400이다`(params: Map<String, String>) {
        // given
        val request = get("/api/admin/llm-call-logs")
        params.forEach { (key, value) -> request.param(key, value) }

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(400)
        val body = mapper.readTree(response.contentAsByteArray)
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message")
        assertThat(body.get("code").asString()).isEqualTo("INVALID_REQUEST")
        assertThat(response.contentAsString).doesNotContain("private-filter")
        verifyNoInteractions(service)
    }

    @ParameterizedTest
    @ValueSource(longs = [1, 9_007_199_254_740_991L])
    @WithMockUser(roles = ["ADMIN"])
    fun `상세는 모든 필드와 파싱한 스냅샷을 반환하고 메시지 원문은 문자열로 유지한다`(id: Long) {
        // given
        val log = log(id).also {
            it.status = LlmCallStatus.FAILED
            it.completedAt = it.startedAt.plusSeconds(2)
            it.durationMs = 1800
            it.responseId = "response-id"
            it.responseModel = "actual-model"
            it.finishReason = "stop"
            it.inputTokens = 0
            it.outputTokens = 3
            it.totalTokens = null
            it.cachedInputTokens = 0
            it.reasoningTokens = 1
            it.failureStage = LlmCallFailureStage.RESPONSE_PARSE
            it.responseData =
                """{"results":[{"index":0,"text":"not-json","finishReason":"stop","metadata":{}}],"metadata":{},"usage":null}"""
            it.errorData = """{"type":"Parse","message":null,"causes":[]}"""
        }
        given(service.getLog(id)).willReturn(log)

        // when
        val response = mockMvc.perform(get("/api/admin/llm-call-logs/$id")).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType))).isEqualTo(MediaType.APPLICATION_JSON)
        val body = mapper.readTree(response.contentAsByteArray)
        assertThat(body.propertyNames()).containsExactlyInAnyOrder(
            "id", "purpose", "status", "provider",
            "requestedModel", "responseModel", "startedAt", "completedAt", "durationMs", "inputTokens",
            "outputTokens", "totalTokens", "failureStage", "questionId", "evaluationAttemptId", "createdAt",
            "responseId", "finishReason", "cachedInputTokens", "reasoningTokens", "requestMessages",
            "requestOptions", "responseData", "errorData"
        )
        assertThat(body.get("requestMessages").isArray).isTrue()
        assertThat(body.get("requestMessages").get(0).get("content").asString()).isEqualTo(" private-answer \n")
        assertThat(body.get("requestOptions").isObject).isTrue()
        assertThat(body.get("responseData").isObject).isTrue()
        assertThat(body.get("responseData").get("results").get(0).get("text").asString()).isEqualTo("not-json")
        assertThat(body.get("errorData").isObject).isTrue()
        assertThat(body.get("inputTokens").asLong()).isZero()
        assertThat(body.get("totalTokens").isNull).isTrue()
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `STARTED 미리보기 상세는 비적용 필드를 누락하지 않고 null로 반환한다`() {
        // given
        given(service.getLog(12)).willReturn(log())

        // when
        val response = mockMvc.perform(get("/api/admin/llm-call-logs/12")).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        val body = mapper.readTree(response.contentAsByteArray)
        listOf(
            "responseModel", "completedAt", "durationMs", "inputTokens", "outputTokens", "totalTokens",
            "failureStage", "questionId", "evaluationAttemptId", "responseId", "finishReason",
            "cachedInputTokens", "reasoningTokens", "responseData", "errorData"
        ).forEach {
            assertThat(body.has(it)).isTrue()
            assertThat(body.get(it).isNull).isTrue()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "9007199254740992", "9223372036854775808", "abc", "1.5"])
    @WithMockUser(roles = ["ADMIN"])
    fun `잘못된 상세 ID는 Service를 호출하지 않고 400이다`(id: String) {
        // given
        val request = get("/api/admin/llm-call-logs/$id")

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(400)
        assertThat(mapper.readTree(response.contentAsByteArray).get("code").asString()).isEqualTo("INVALID_REQUEST")
        verifyNoInteractions(service)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `없는 상세는 호출 기록 전용 404이고 내부 오류는 안전한 500이다`() {
        // given
        given(service.getLog(12)).willThrow(LlmCallLogNotFoundException())
        given(service.getLog(13)).willThrow(IllegalStateException("private-answer"))
        given(service.getLogs(LlmCallLogFilter())).willThrow(IllegalStateException("private-answer"))

        // when
        val missing = mockMvc.perform(get("/api/admin/llm-call-logs/12")).andReturn().response
        val failed = mockMvc.perform(get("/api/admin/llm-call-logs/13")).andReturn().response
        val listFailed = mockMvc.perform(get("/api/admin/llm-call-logs")).andReturn().response

        // then
        assertThat(missing.status).isEqualTo(404)
        assertThat(
            mapper.readTree(missing.contentAsByteArray).get("code").asString()
        ).isEqualTo("LLM_CALL_LOG_NOT_FOUND")
        listOf(failed, listFailed).forEach {
            assertThat(it.status).isEqualTo(500)
            assertThat(mapper.readTree(it.contentAsByteArray).get("code").asString()).isEqualTo("INTERNAL_SERVER_ERROR")
            assertThat(it.contentAsString).doesNotContain("private-answer")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["not-json-private-answer", "null", "[]", "{} {}"])
    @WithMockUser(roles = ["ADMIN"])
    fun `잘못된 저장 스냅샷은 원문을 노출하지 않는 500이다`(options: String) {
        // given
        val log = LlmCallLog(
            LlmCallPurpose.ADMIN_PREVIEW,
            null,
            "gateway",
            "model",
            Instant.now(),
            "[]",
            options
        )
        ReflectionTestUtils.setField(log, "id", 12L)
        ReflectionTestUtils.setField(log, "createdAt", Instant.now())
        given(service.getLog(12)).willReturn(log)

        // when
        val response = mockMvc.perform(get("/api/admin/llm-call-logs/12")).andReturn().response

        // then
        assertThat(response.status).isEqualTo(500)
        assertThat(
            mapper.readTree(response.contentAsByteArray).get("code").asString()
        ).isEqualTo("INTERNAL_SERVER_ERROR")
        assertThat(response.contentAsString).doesNotContain("private-answer")
    }

    @Test
    fun `익명 사용자는 목록과 상세 모두 인증 필요 오류다`() {
        // given
        val paths = listOf("/api/admin/llm-call-logs", "/api/admin/llm-call-logs/12")

        // when
        val responses = paths.map { mockMvc.perform(get(it)).andReturn().response }

        // then
        responses.forEach {
            assertThat(it.status).isEqualTo(401)
            assertThat(
                mapper.readTree(it.contentAsByteArray).get("code").asString()
            ).isEqualTo("AUTHENTICATION_REQUIRED")
        }
        verifyNoInteractions(service)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `일반 사용자는 목록과 상세 모두 접근 거부 오류다`() {
        // given
        val paths = listOf("/api/admin/llm-call-logs", "/api/admin/llm-call-logs/12")

        // when
        val responses = paths.map { mockMvc.perform(get(it)).andReturn().response }

        // then
        responses.forEach {
            assertThat(it.status).isEqualTo(403)
            assertThat(mapper.readTree(it.contentAsByteArray).get("code").asString()).isEqualTo("ACCESS_DENIED")
        }
        verifyNoInteractions(service)
    }

    private fun log(id: Long = 12) = LlmCallLog(
        LlmCallPurpose.ADMIN_PREVIEW,
        null,
        "gateway",
        "model",
        Instant.parse("2026-10-25T10:00:00Z"),
        """[{"role":"user","content":" private-answer \n"}]""",
        "{}"
    ).also {
        ReflectionTestUtils.setField(it, "id", id)
        ReflectionTestUtils.setField(it, "createdAt", it.startedAt.plusMillis(1))
    }

    companion object {
        @JvmStatic
        fun invalidQueries(): List<Map<String, String>> {
            val result = mutableListOf<Map<String, String>>()
            mapOf(
                "page" to listOf("", "-1", "2147483647", "999999999999999999999", "1.5", " 1", "+1", "private-filter"),
                "size" to listOf("", "0", "101", "-1", "1.5", "private-filter"),
                "status" to listOf("", "failed", "UNKNOWN", " FAILED "),
                "purpose" to listOf("", "admin_preview", "UNKNOWN"),
                "model" to listOf("", "  ", "a".repeat(256)),
                "startedFrom" to listOf("", "2026-10-25T00:00:00", "2026-10-25", "private-filter"),
                "startedTo" to listOf("", "2026-10-25T00:00:00", "2026-02-30T00:00:00Z", "private-filter"),
            ).forEach { (key, values) -> values.forEach { result += mapOf(key to it) } }
            result += mapOf("startedFrom" to "2026-10-25T00:00:00Z", "startedTo" to "2026-10-25T00:00:00Z")
            result += mapOf("startedFrom" to "2026-10-26T00:00:00Z", "startedTo" to "2026-10-25T00:00:00Z")
            return result
        }
    }
}
