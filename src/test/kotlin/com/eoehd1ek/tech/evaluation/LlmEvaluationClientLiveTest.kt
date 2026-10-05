package com.eoehd1ek.tech.evaluation

import com.eoehd1ek.tech.question.EvaluationCriterion
import com.eoehd1ek.tech.question.Question
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.test.util.ReflectionTestUtils
import tools.jackson.databind.json.JsonMapper

// Opt in only when running this test explicitly; SDK retries may incur additional cost.
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_LLM_TEST", matches = "true")
class LlmEvaluationClientLiveTest {
    @Test
    fun `실제 LLM은 예시 질문과 기준과 답변에 계약에 맞는 JSON 평가를 반환한다`() {
        // given
        val settings = listOf("CHAT_MODEL", "CHAT_BASE_URL", "CHAT_API_KEY").associateWith { name ->
            requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) { "$name is required" }
        }
        val model = OpenAiChatModel.builder()
            .options(OpenAiChatOptions.builder()
                .model(settings.getValue("CHAT_MODEL"))
                .baseUrl(settings.getValue("CHAT_BASE_URL"))
                .apiKey(settings.getValue("CHAT_API_KEY"))
                .build())
            .build()
        val client = LlmEvaluationClient(model)
        val question = Question("데이터베이스 인덱스", "인덱스의 목적과 탐색 원리, 장단점을 설명해주세요.")
        val criteria = listOf(
            EvaluationCriterion(1L, "인덱스의 목적과 조회 성능 향상을 설명한다", 30, 1),
            EvaluationCriterion(1L, "B-Tree 기반 탐색 원리를 설명한다", 30, 2),
            EvaluationCriterion(1L, "저장 공간과 데이터 변경 시 유지 비용을 설명한다", 40, 3),
        )
        criteria.forEachIndexed { index, criterion ->
            ReflectionTestUtils.setField(criterion, "id", 101L + index)
        }
        val answer = """
            인덱스는 전체 행을 순회하지 않고 필요한 데이터를 빠르게 찾도록 돕는 자료구조입니다.
            B-Tree는 정렬된 키와 자식 포인터를 이용해 루트부터 탐색 범위를 좁혀 데이터를 찾습니다.
            조회 성능을 높일 수 있지만 별도 저장 공간을 사용하고 INSERT, UPDATE, DELETE 시
            인덱스도 갱신해야 하므로 쓰기 비용이 증가합니다.
        """.trimIndent()

        // when
        val content = client.evaluate(question, criteria, answer)

        // then
        val evaluated = EvaluationResponseValidator().validate(content, criteria)
        assertThat(evaluated.score).isBetween(0, 100)
        val root = JsonMapper.builder().build().readTree(content)
        assertThat(root.propertyNames()).containsExactlyInAnyOrder(
            "criteria", "strengths", "weaknesses", "improvements",
        )
        root.get("criteria").forEach { item ->
            assertThat(item.propertyNames()).containsExactlyInAnyOrder("criterionId", "score", "feedback")
        }
    }
}
