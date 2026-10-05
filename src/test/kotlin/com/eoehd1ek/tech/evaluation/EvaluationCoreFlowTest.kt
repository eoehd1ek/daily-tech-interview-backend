package com.eoehd1ek.tech.evaluation

import com.eoehd1ek.tech.question.EvaluationCriterion
import com.eoehd1ek.tech.question.EvaluationCriterionRepository
import com.eoehd1ek.tech.question.Question
import com.eoehd1ek.tech.question.QuestionRepository
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
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EvaluationCoreFlowTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var questionRepository: QuestionRepository

    @Autowired
    private lateinit var criterionRepository: EvaluationCriterionRepository

    @Autowired
    private lateinit var attemptRepository: EvaluationAttemptRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var model: OpenAiChatModel

    @Test
    fun `질문 조회부터 제출 commit과 반복 HTTP 결과 조회까지 외부 LLM 없이 연결한다`() {
        // given
        val question = questionRepository.save(Question("연결 검증 질문", "가상의 정보로 답변해주세요."))
        val questionId = requireNotNull(question.id)
        val criterion = criterionRepository.save(EvaluationCriterion(questionId, "연결 검증 기준", 100, 1))
        val answer = "  가상의 답변\n원문을 보존합니다.  "
        given(model.options).willReturn(OpenAiChatOptions.builder().model("test-model").build())
        given(model.call(any(Prompt::class.java))).willReturn(ChatResponse(listOf(Generation(AssistantMessage("""
            {"criteria":[{"criterionId":${criterion.id},"score":85,"feedback":"설명했습니다."}],
             "strengths":"장점", "weaknesses":"단점", "improvements":"개선점"}
        """.trimIndent())))))
        val client = HttpClient.newHttpClient()
        val base = "http://localhost:$port"
        val submitRequest = HttpRequest.newBuilder(URI.create("$base/api/questions/$questionId/evaluation-attempts"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(mapOf("answer" to answer))))
            .build()

        // when
        val list = client.send(HttpRequest.newBuilder(URI.create("$base/api/questions")).GET().build(),
            HttpResponse.BodyHandlers.ofString())
        val detail = client.send(HttpRequest.newBuilder(URI.create("$base/api/questions/$questionId")).GET().build(),
            HttpResponse.BodyHandlers.ofString())
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
        assertThat(Duration.between(Instant.parse(postedBody.get("createdAt").asString()),
            Instant.parse(resultBody.get("createdAt").asString())).abs()).isLessThan(Duration.ofNanos(1_000))
        assertThat(objectMapper.readTree(refreshed.body())).isEqualTo(resultBody)
        assertThat(attemptRepository.findById(id).orElseThrow().answer).isEqualTo(answer)
        assertThat(resubmitted.statusCode()).isEqualTo(201)
        assertThat(secondBody.get("id").asLong()).isPositive().isNotEqualTo(id)
        assertThat(attemptRepository.findById(secondBody.get("id").asLong()).orElseThrow().answer).isEqualTo(answer)
        verify(model, times(2)).call(any(Prompt::class.java))
    }
}
