package com.eoehd1ek.tech.evaluation.integration

import com.eoehd1ek.tech.evaluation.domain.EvaluationAttempt
import com.eoehd1ek.tech.evaluation.domain.EvaluationResult
import com.eoehd1ek.tech.evaluation.infrastructure.persistence.EvaluationAttemptRepository
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallFailureStage
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallPurpose
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallStatus
import com.eoehd1ek.tech.llmcalllog.infrastructure.persistence.LlmCallLogRepository
import com.eoehd1ek.tech.question.domain.EvaluationCriterion
import com.eoehd1ek.tech.question.domain.Question
import com.eoehd1ek.tech.question.infrastructure.persistence.EvaluationCriterionRepository
import com.eoehd1ek.tech.question.infrastructure.persistence.QuestionRepository
import com.eoehd1ek.tech.question.presentation.request.AdminCriterionRequest
import com.eoehd1ek.tech.question.presentation.request.AdminQuestionRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.ObjectMapper
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EvaluationCoreFlowTest {
    @LocalServerPort
    private var port: Int = 0

    @Value("\${local.management.port}")
    private var managementPort: Int = 0

    @Autowired
    private lateinit var questionRepository: QuestionRepository

    @Autowired
    private lateinit var criterionRepository: EvaluationCriterionRepository

    @Autowired
    private lateinit var attemptRepository: EvaluationAttemptRepository

    @Autowired
    private lateinit var logRepository: LlmCallLogRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var model: OpenAiChatModel

    @Test
    fun `메트릭은 내부 관리 포트에서만 제공하고 공개 API는 기존대로 동작한다`() {
        // given
        val client = HttpClient.newHttpClient()
        val applicationBase = "http://localhost:$port"
        val managementBase = "http://localhost:$managementPort"

        // when
        val questions = client.send(
            HttpRequest.newBuilder(URI.create("$applicationBase/api/questions")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        val health = client.send(
            HttpRequest.newBuilder(URI.create("$managementBase/actuator/health")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        val metrics = client.send(
            HttpRequest.newBuilder(URI.create("$managementBase/actuator/prometheus")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        val blocked = client.send(
            HttpRequest.newBuilder(URI.create("$applicationBase/actuator/prometheus")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

        // then
        assertThat(questions.statusCode()).isEqualTo(200)
        assertThat(health.statusCode()).isEqualTo(200)
        assertThat(objectMapper.readTree(health.body()).get("status").asString()).isEqualTo("UP")
        assertThat(objectMapper.readTree(health.body()).has("components")).isFalse()
        assertThat(metrics.statusCode()).isEqualTo(200)
        assertThat(metrics.body()).contains(
            "jvm_memory_used_bytes",
            "http_server_requests_seconds_count",
            "hikaricp_connections"
        )
        assertThat(blocked.statusCode()).isEqualTo(401)
    }

    @Test
    fun `질문 조회부터 제출 commit과 반복 HTTP 결과 조회까지 외부 LLM 없이 연결한다`() {
        // given
        val question = questionRepository.save(Question("연결 검증 질문", "가상의 정보로 답변해주세요."))
        val questionId = requireNotNull(question.id)
        val criterion = criterionRepository.save(EvaluationCriterion(questionId, "연결 검증 기준", 100, 1))
        val answer = "  가상의 답변\n원문을 보존합니다.  "
        val rawResponse = """
            {"criteria":[{"criterionId":${criterion.id},"score":85,"feedback":"설명했습니다."}],
             "strengths":"장점", "weaknesses":"단점", "improvements":"개선점"}
        """.trimIndent()
        given(model.options).willReturn(OpenAiChatOptions.builder().model("test-model").build())
        given(model.call(any(Prompt::class.java)))
            .willAnswer {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
                val startedLogs = logRepository.findAll()
                    .filter { it.questionId == questionId && it.status == LlmCallStatus.STARTED }
                assertThat(startedLogs).hasSize(1)
                val startedLog = startedLogs.single()
                assertThat(startedLog.createdAt).isNotNull()
                assertThat(startedLog.completedAt).isNull()
                assertThat(startedLog.durationMs).isNull()
                assertThat(startedLog.evaluationAttemptId).isNull()
                assertThat(startedLog.responseData).isNull()
                assertThat(startedLog.errorData).isNull()
                assertThat(startedLog.failureStage).isNull()
                ChatResponse(listOf(Generation(AssistantMessage(rawResponse))))
            }
        val client = HttpClient.newHttpClient()
        val base = "http://localhost:$port"
        val submitRequest = HttpRequest.newBuilder(URI.create("$base/api/questions/$questionId/evaluation-attempts"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(mapOf("answer" to answer))))
            .build()

        // when
        val list = client.send(
            HttpRequest.newBuilder(URI.create("$base/api/questions")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        val detail = client.send(
            HttpRequest.newBuilder(URI.create("$base/api/questions/$questionId")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        val submitted = client.send(submitRequest, HttpResponse.BodyHandlers.ofString())
        val postedBody = objectMapper.readTree(submitted.body())
        val id = postedBody.get("id").asLong()
        val resultRequest = HttpRequest.newBuilder(URI.create("$base/api/evaluation-attempts/$id")).GET().build()
        val result = client.send(resultRequest, HttpResponse.BodyHandlers.ofString())
        val refreshed = client.send(resultRequest, HttpResponse.BodyHandlers.ofString())
        val resubmitted = client.send(submitRequest, HttpResponse.BodyHandlers.ofString())
        val secondBody = objectMapper.readTree(resubmitted.body())

        // then
        assertThat(list.statusCode()).isEqualTo(200)
        assertThat(objectMapper.readTree(list.body()).toList().map { it.get("id").asLong() }).contains(questionId)
        assertThat(detail.statusCode()).isEqualTo(200)
        assertThat(objectMapper.readTree(detail.body()).get("content").asString()).isEqualTo(question.content)
        assertThat(submitted.statusCode()).isEqualTo(201)
        assertThat(submitted.headers().firstValue("Location").orElseThrow()).isEqualTo("/api/evaluation-attempts/$id")
        assertThat(id).isPositive()
        assertThat(postedBody.propertyNames()).containsExactlyInAnyOrder(
            "id", "questionId", "questionTitle", "answer", "score", "result",
            "strengths", "weaknesses", "improvements", "createdAt",
        )
        assertThat(postedBody.get("answer").asString()).isEqualTo(answer)
        assertThat(postedBody.get("score").asInt()).isEqualTo(85)
        assertThat(postedBody.get("result").asString()).isEqualTo("PASS")
        assertThat(Instant.parse(postedBody.get("createdAt").asString())).isNotNull()
        assertThat(result.statusCode()).isEqualTo(200)
        assertThat(refreshed.statusCode()).isEqualTo(200)
        val resultBody = objectMapper.readTree(result.body())
        assertThat(resultBody.propertyNames()).containsExactlyInAnyOrderElementsOf(postedBody.propertyNames())
        assertThat(postedBody.propertyNames().filter { it != "createdAt" }).allSatisfy { field ->
            assertThat(resultBody.get(field)).isEqualTo(postedBody.get(field))
        }
        // PostgreSQL stores Instant at microsecond precision; POST uses the audited save return value.
        assertThat(
            Duration.between(
                Instant.parse(postedBody.get("createdAt").asString()),
                Instant.parse(resultBody.get("createdAt").asString())
            ).abs()
        ).isLessThan(Duration.ofNanos(1_000))
        assertThat(objectMapper.readTree(refreshed.body())).isEqualTo(resultBody)
        assertThat(attemptRepository.findById(id).orElseThrow().answer).isEqualTo(answer)
        assertThat(resubmitted.statusCode()).isEqualTo(201)
        assertThat(secondBody.get("id").asLong()).isPositive().isNotEqualTo(id)
        assertThat(attemptRepository.findById(secondBody.get("id").asLong()).orElseThrow().answer).isEqualTo(answer)
        val logs = logRepository.findAll().filter { it.questionId == questionId }
        assertThat(logs).hasSize(2)
        assertThat(logs.map { it.evaluationAttemptId }).containsExactlyInAnyOrder(id, secondBody.get("id").asLong())
        assertThat(logs).allSatisfy { log ->
            assertThat(log.purpose).isEqualTo(LlmCallPurpose.ANSWER_EVALUATION)
            assertThat(log.status).isEqualTo(LlmCallStatus.SUCCEEDED)
            assertThat(log.questionId).isEqualTo(questionId)
            assertThat(log.requestedModel).isEqualTo("test-model")
            assertThat(log.completedAt).isNotNull()
            assertThat(log.durationMs).isNotNull().isGreaterThanOrEqualTo(0L)
            assertThat(log.failureStage).isNull()
            assertThat(log.errorData).isNull()
            assertThat(log.inputTokens).isNull()
            assertThat(log.outputTokens).isNull()
            assertThat(log.totalTokens).isNull()
            assertThat(log.cachedInputTokens).isNull()
            assertThat(log.reasoningTokens).isNull()
            val messages = objectMapper.readTree(log.requestMessages)
            assertThat(messages.toList().map { it.get("role").asString() }).containsExactly("system", "user")
            assertThat(messages.get(0).get("content").asString()).isNotBlank()
            val requestData = objectMapper.readTree(messages.get(1).get("content").asString())
            assertThat(requestData.get("answer").asString()).isEqualTo(answer)
            assertThat(requestData.get("question").get("title").asString()).isEqualTo(question.title)
            assertThat(requestData.get("question").get("content").asString()).isEqualTo(question.content)
            assertThat(requestData.get("criteria").get(0).get("criterionId").asLong()).isEqualTo(criterion.id)
            assertThat(requestData.get("criteria").get(0).get("content").asString()).isEqualTo(criterion.content)
            val options = objectMapper.readTree(log.requestOptions)
            assertThat(options.get("responseFormat").propertyNames()).containsExactlyInAnyOrder("type", "strict")
            assertThat(options.toString()).doesNotContain("additionalProperties", "jsonSchema", "\"schema\"")
            val responseData = objectMapper.readTree(requireNotNull(log.responseData))
            assertThat(responseData.get("results").get(0).get("text").asString()).isEqualTo(rawResponse)
            assertThat(responseData.get("usage").isNull).isTrue()
        }
        verify(model, times(2)).call(any(Prompt::class.java))
    }

    @Test
    fun `응답 파싱 실패로 502를 반환해도 실패 로그와 응답 원문은 commit된다`() {
        // given
        val question = questionRepository.save(Question("파싱 실패 검증 질문", "실패한 응답을 검증합니다."))
        val questionId = requireNotNull(question.id)
        criterionRepository.save(EvaluationCriterion(questionId, "파싱 실패 검증 기준", 100, 1))
        val answer = "  실패해도 요청 원문을 보존합니다.\n  "
        val rawResponse = "  invalid JSON response\n원문 유지  "
        given(model.options).willReturn(OpenAiChatOptions.builder().model("test-model").build())
        given(model.call(any(Prompt::class.java)))
            .willReturn(ChatResponse(listOf(Generation(AssistantMessage(rawResponse)))))
        val client = HttpClient.newHttpClient()
        val request =
            HttpRequest.newBuilder(URI.create("http://localhost:$port/api/questions/$questionId/evaluation-attempts"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(mapOf("answer" to answer))))
                .build()

        // when
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val logs = logRepository.findAll().filter { it.questionId == questionId }

        // then
        assertThat(response.statusCode()).isEqualTo(502)
        val body = objectMapper.readTree(response.body())
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message")
        assertThat(body.get("code").asString()).isEqualTo("LLM_EVALUATION_FAILED")
        assertThat(response.body()).doesNotContain(rawResponse, answer)
        assertThat(attemptRepository.findAll().filter { it.questionId == questionId }).isEmpty()
        assertThat(logs).hasSize(1)
        val log = logs.single()
        assertThat(log.purpose).isEqualTo(LlmCallPurpose.ANSWER_EVALUATION)
        assertThat(log.status).isEqualTo(LlmCallStatus.FAILED)
        assertThat(log.questionId).isEqualTo(questionId)
        assertThat(log.evaluationAttemptId).isNull()
        assertThat(log.failureStage).isEqualTo(LlmCallFailureStage.RESPONSE_PARSE)
        assertThat(log.completedAt).isNotNull()
        assertThat(log.durationMs).isNotNull().isGreaterThanOrEqualTo(0L)
        assertThat(log.createdAt).isNotNull()
        val messages = objectMapper.readTree(log.requestMessages)
        val requestData = objectMapper.readTree(messages.get(1).get("content").asString())
        assertThat(requestData.get("answer").asString()).isEqualTo(answer)
        val responseData = objectMapper.readTree(requireNotNull(log.responseData))
        assertThat(responseData.get("results").get(0).get("text").asString()).isEqualTo(rawResponse)
        assertThat(responseData.get("usage").isNull).isTrue()
        assertThat(objectMapper.readTree(requireNotNull(log.errorData)).propertyNames())
            .containsExactlyInAnyOrder("type", "message", "causes")
        verify(model, times(1)).call(any(Prompt::class.java))
    }

    @Test
    fun `관리자 수정은 공개 조회에 반영되고 미리 평가 로그 원문은 관리자만 조회한다`() {
        // given
        val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        val client = HttpClient.newBuilder().cookieHandler(cookies).build()
        val base = "http://localhost:$port"
        val initialToken = client.send(
            HttpRequest.newBuilder(URI.create("$base/api/auth/csrf")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        val token = objectMapper.readTree(initialToken.body())
        val login = client.send(
            HttpRequest.newBuilder(URI.create("$base/api/auth/login"))
                .header("Content-Type", "application/json")
                .header(token.get("headerName").asString(), token.get("token").asString())
                .POST(HttpRequest.BodyPublishers.ofString("""{"loginId":"integration-admin","password":"test-only-password"}"""))
                .build(), HttpResponse.BodyHandlers.ofString()
        )
        assertThat(login.statusCode()).isEqualTo(200)
        val refreshedToken = client.send(
            HttpRequest.newBuilder(URI.create("$base/api/auth/csrf")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        val csrf = objectMapper.readTree(refreshedToken.body())
        val me = client.send(
            HttpRequest.newBuilder(URI.create("$base/api/auth/me")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertThat(me.statusCode()).isEqualTo(200)
        assertThat(objectMapper.readTree(me.body()).get("role").asString()).isEqualTo("ADMIN")
        val original = AdminQuestionRequest("생성 제목", "생성 본문", listOf(AdminCriterionRequest("기존 기준", 100, 10)))
        val changed = AdminQuestionRequest(
            "수정 제목", "수정 본문", listOf(
                AdminCriterionRequest("새 둘째 기준", 60, 20), AdminCriterionRequest("새 첫 기준", 40, -5),
            )
        )
        val createRequest = HttpRequest.newBuilder(URI.create("$base/api/admin/questions"))
            .header("Content-Type", "application/json")
            .header(csrf.get("headerName").asString(), csrf.get("token").asString())
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(original))).build()
        val previewAnswer = "  관리자 미리 평가 답변\n원문 보존  "
        val rawResponse = """
            {"criteria":[{"criterionId":1,"score":80,"feedback":"설명했습니다."}],
             "strengths":"장점", "weaknesses":"단점", "improvements":"개선점"}
        """.trimIndent()
        given(model.options).willReturn(OpenAiChatOptions.builder().model("integration-admin-log-model").build())
        given(model.call(any(Prompt::class.java)))
            .willReturn(ChatResponse(listOf(Generation(AssistantMessage(rawResponse)))))

        // when
        val preview = client.send(
            HttpRequest.newBuilder(URI.create("$base/api/admin/questions/evaluation-preview"))
                .header("Content-Type", "application/json")
                .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(
                            mapOf(
                                "title" to original.title, "content" to original.content,
                                "criteria" to original.criteria, "answer" to previewAnswer,
                            )
                        )
                    )
                ).build(), HttpResponse.BodyHandlers.ofString()
        )
        val logs = client.send(
            HttpRequest.newBuilder(
                URI.create(
                    "$base/api/admin/llm-call-logs?purpose=ADMIN_PREVIEW&model=integration-admin-log-model&size=100",
                )
            ).GET().build(), HttpResponse.BodyHandlers.ofString()
        )
        assertThat(logs.statusCode()).isEqualTo(200)
        val logsBody = objectMapper.readTree(logs.body())
        assertThat(logsBody.get("items").toList()).hasSize(1)
        val logId = logsBody.get("items").get(0).get("id").asLong()
        val logDetail = client.send(
            HttpRequest.newBuilder(URI.create("$base/api/admin/llm-call-logs/$logId"))
                .GET().build(), HttpResponse.BodyHandlers.ofString()
        )
        val anonymous = HttpClient.newHttpClient()
        val anonymousLogs = anonymous.send(
            HttpRequest.newBuilder(URI.create("$base/api/admin/llm-call-logs"))
                .GET().build(), HttpResponse.BodyHandlers.ofString()
        )
        val anonymousDetail = anonymous.send(
            HttpRequest.newBuilder(URI.create("$base/api/admin/llm-call-logs/$logId"))
                .GET().build(), HttpResponse.BodyHandlers.ofString()
        )
        val created = client.send(createRequest, HttpResponse.BodyHandlers.ofString())
        val createdBody = objectMapper.readTree(created.body())
        val questionId = createdBody.get("id").asLong()
        val oldCriterionId = createdBody.get("criteria").get(0).get("id").asLong()
        val attempt = attemptRepository.save(
            EvaluationAttempt(
                questionId, "보존할 답변", 80, EvaluationResult.PASS, "장점", "단점", "개선점",
            )
        )
        val updated = client.send(
            HttpRequest.newBuilder(URI.create("$base/api/admin/questions/$questionId"))
                .header("Content-Type", "application/json")
                .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(changed))).build(),
            HttpResponse.BodyHandlers.ofString()
        )
        val detail = client.send(
            HttpRequest.newBuilder(URI.create("$base/api/questions/$questionId")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        val list = client.send(
            HttpRequest.newBuilder(URI.create("$base/api/questions")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        val result = client.send(
            HttpRequest.newBuilder(
                URI.create("$base/api/evaluation-attempts/${attempt.id}")
            ).GET().build(), HttpResponse.BodyHandlers.ofString()
        )

        // then
        assertThat(preview.statusCode()).isEqualTo(200)
        assertThat(objectMapper.readTree(preview.body()).get("score").asInt()).isEqualTo(80)
        assertThat(logsBody.propertyNames()).containsExactlyInAnyOrder(
            "items",
            "page",
            "size",
            "totalElements",
            "totalPages"
        )
        assertThat(logsBody.get("page").asInt()).isZero()
        assertThat(logsBody.get("size").asInt()).isEqualTo(100)
        assertThat(logsBody.get("totalElements").asLong()).isEqualTo(1L)
        assertThat(logsBody.get("totalPages").asInt()).isEqualTo(1)
        val summary = logsBody.get("items").get(0)
        assertThat(summary.get("status").asString()).isEqualTo("SUCCEEDED")
        assertThat(summary.get("purpose").asString()).isEqualTo("ADMIN_PREVIEW")
        assertThat(summary.propertyNames()).containsExactlyInAnyOrder(
            "id", "purpose", "status", "provider", "requestedModel", "responseModel", "startedAt",
            "completedAt", "durationMs", "inputTokens", "outputTokens", "totalTokens", "failureStage",
        )
        assertThat(logDetail.statusCode()).isEqualTo(200)
        val logBody = objectMapper.readTree(logDetail.body())
        assertThat(logBody.get("id").asLong()).isEqualTo(logId)
        assertThat(logBody.get("questionId").isNull).isTrue()
        assertThat(logBody.get("evaluationAttemptId").isNull).isTrue()
        assertThat(logBody.get("requestMessages").isArray).isTrue()
        assertThat(logBody.get("requestOptions").isObject).isTrue()
        val previewData = objectMapper.readTree(logBody.get("requestMessages").get(1).get("content").asString())
        assertThat(previewData.get("answer").asString()).isEqualTo(previewAnswer)
        assertThat(previewData.get("question").get("title").asString()).isEqualTo(original.title)
        assertThat(logBody.get("responseData").get("results").get(0).get("text").asString()).isEqualTo(rawResponse)
        assertThat(logBody.get("responseData").get("usage").isNull).isTrue()
        assertThat(logBody.get("inputTokens").isNull).isTrue()
        assertThat(logBody.get("outputTokens").isNull).isTrue()
        assertThat(logBody.get("totalTokens").isNull).isTrue()
        assertThat(logBody.get("requestOptions").get("responseFormat").propertyNames())
            .containsExactlyInAnyOrder("type", "strict")
        assertThat(anonymousLogs.statusCode()).isEqualTo(401)
        assertThat(anonymousDetail.statusCode()).isEqualTo(401)
        assertThat(
            objectMapper.readTree(anonymousLogs.body()).get("code").asString()
        ).isEqualTo("AUTHENTICATION_REQUIRED")
        assertThat(
            objectMapper.readTree(anonymousDetail.body()).get("code").asString()
        ).isEqualTo("AUTHENTICATION_REQUIRED")
        assertThat(anonymousDetail.body()).doesNotContain(previewAnswer, rawResponse)
        assertThat(created.statusCode()).isEqualTo(201)
        assertThat(created.headers().firstValue("Location").orElseThrow()).isEqualTo("/api/admin/questions/$questionId")
        assertThat(updated.statusCode()).isEqualTo(200)
        assertThat(objectMapper.readTree(updated.body()).get("id").asLong()).isEqualTo(questionId)
        assertThat(criterionRepository.findById(oldCriterionId)).isEmpty()
        val criteria = criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)
        assertThat(criteria.map { it.content }).containsExactly("새 첫 기준", "새 둘째 기준")
        assertThat(criteria.map { it.maxScore }).containsExactly(40, 60)
        assertThat(criteria.map { it.displayOrder }).containsExactly(-5, 20)
        assertThat(detail.statusCode()).isEqualTo(200)
        val detailBody = objectMapper.readTree(detail.body())
        assertThat(detailBody.propertyNames()).containsExactlyInAnyOrder("id", "title", "content")
        assertThat(detailBody.get("title").asString()).isEqualTo(changed.title)
        assertThat(detailBody.get("content").asString()).isEqualTo(changed.content)
        assertThat(list.statusCode()).isEqualTo(200)
        assertThat(objectMapper.readTree(list.body()).toList().filter { it.get("id").asLong() == questionId }
            .map { it.get("title").asString() }).containsExactly(changed.title)
        assertThat(result.statusCode()).isEqualTo(200)
        val resultBody = objectMapper.readTree(result.body())
        assertThat(resultBody.get("questionTitle").asString()).isEqualTo(changed.title)
        assertThat(resultBody.get("answer").asString()).isEqualTo(attempt.answer)
        assertThat(resultBody.get("score").asInt()).isEqualTo(attempt.score)
        assertThat(attemptRepository.findById(requireNotNull(attempt.id)).orElseThrow())
            .usingRecursiveComparison().ignoringFields("createdAt").isEqualTo(attempt)
        verify(model, times(1)).call(any(Prompt::class.java))
    }
}
