package com.eoehd1ek.tech.evaluation

import com.eoehd1ek.tech.question.EvaluationCriterion
import com.eoehd1ek.tech.question.EvaluationCriterionRepository
import com.eoehd1ek.tech.question.Question
import com.eoehd1ek.tech.question.QuestionRepository
import com.eoehd1ek.tech.question.AdminQuestionRequest
import com.eoehd1ek.tech.question.AdminCriterionRequest
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

    @Test
    fun `관리자 생성과 수정은 공개 조회에 반영되고 기존 평가 기록은 유지된다`() {
        // given
        val client = HttpClient.newHttpClient()
        val base = "http://localhost:$port"
        val original = AdminQuestionRequest("생성 제목", "생성 본문", listOf(AdminCriterionRequest("기존 기준", 100)))
        val changed = AdminQuestionRequest("수정 제목", "수정 본문", listOf(
            AdminCriterionRequest("새 첫 기준", 40), AdminCriterionRequest("새 둘째 기준", 60),
        ))
        val createRequest = HttpRequest.newBuilder(URI.create("$base/api/admin/questions"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(original))).build()

        // when
        val created = client.send(createRequest, HttpResponse.BodyHandlers.ofString())
        val createdBody = objectMapper.readTree(created.body())
        val questionId = createdBody.get("id").asLong()
        val oldCriterionId = createdBody.get("criteria").get(0).get("id").asLong()
        val attempt = attemptRepository.save(EvaluationAttempt(
            questionId, "보존할 답변", 80, EvaluationResult.PASS, "장점", "단점", "개선점",
        ))
        val updated = client.send(HttpRequest.newBuilder(URI.create("$base/api/admin/questions/$questionId"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(changed))).build(),
            HttpResponse.BodyHandlers.ofString())
        val detail = client.send(HttpRequest.newBuilder(URI.create("$base/api/questions/$questionId")).GET().build(),
            HttpResponse.BodyHandlers.ofString())
        val list = client.send(HttpRequest.newBuilder(URI.create("$base/api/questions")).GET().build(),
            HttpResponse.BodyHandlers.ofString())
        val result = client.send(HttpRequest.newBuilder(
            URI.create("$base/api/evaluation-attempts/${attempt.id}")).GET().build(), HttpResponse.BodyHandlers.ofString())

        // then
        assertThat(created.statusCode()).isEqualTo(201)
        assertThat(created.headers().firstValue("Location").orElseThrow()).isEqualTo("/api/admin/questions/$questionId")
        assertThat(updated.statusCode()).isEqualTo(200)
        assertThat(objectMapper.readTree(updated.body()).get("id").asLong()).isEqualTo(questionId)
        assertThat(criterionRepository.findById(oldCriterionId)).isEmpty()
        val criteria = criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)
        assertThat(criteria.map { it.content }).containsExactly("새 첫 기준", "새 둘째 기준")
        assertThat(criteria.map { it.maxScore }).containsExactly(40, 60)
        assertThat(criteria.map { it.displayOrder }).containsExactly(1, 2)
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
    }
}
