package com.eoehd1ek.tech.evaluation

import com.eoehd1ek.tech.question.EvaluationCriterion
import com.eoehd1ek.tech.question.EvaluationCriterionRepository
import com.eoehd1ek.tech.question.Question
import com.eoehd1ek.tech.question.QuestionNotFoundException
import com.eoehd1ek.tech.question.QuestionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class EvaluationServiceTest {
    @Mock
    private lateinit var questionRepository: QuestionRepository

    @Mock
    private lateinit var criterionRepository: EvaluationCriterionRepository

    @Mock
    private lateinit var llmClient: LlmEvaluationClient

    private lateinit var service: EvaluationService

    @BeforeEach
    fun setUp() {
        service = EvaluationService(questionRepository, criterionRepository, llmClient, EvaluationResponseValidator())
    }

    private val questionId = 10L
    private val answer = "테스트 답변"
    private val question = Question("테스트 질문", "테스트 본문")
    private val criteria = listOf(EvaluationCriterion(questionId, "테스트 기준", 100, 1).apply {
        ReflectionTestUtils.setField(this, "id", 20L)
    })

    @Test
    fun `LLM 응답으로 점수와 판정과 피드백을 생성한다`() {
        // given
        givenRepositories()
        given(llmClient.evaluate(question, criteria, answer)).willReturn("""
            {"criteria":[{"criterionId":20,"score":85,"feedback":"잘 설명했습니다."}],
             "strengths":"장점", "weaknesses":"단점", "improvements":"개선점"}
        """.trimIndent())

        // when
        val result = service.evaluate(questionId, answer)

        // then
        assertThat(result).isEqualTo(EvaluatedAnswer(85, EvaluationResult.PASS, "장점", "단점", "개선점"))
        verify(llmClient).evaluate(question, criteria, answer)
        verifyNoMoreInteractions(llmClient)
    }

    @ParameterizedTest
    @ValueSource(strings = ["not JSON", "{}"])
    fun `잘못된 LLM 응답은 재호출 없이 안전한 평가 실패로 처리한다`(content: String) {
        // given
        givenRepositories()
        given(llmClient.evaluate(question, criteria, answer)).willReturn(content)

        // when
        val action = { service.evaluate(questionId, answer) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(LlmEvaluationFailedException::class.java).hasNoCause()
        verify(llmClient).evaluate(question, criteria, answer)
        verifyNoMoreInteractions(llmClient)
    }

    @Test
    fun `LLM 호출 실패는 애플리케이션 재호출 없이 전달한다`() {
        // given
        givenRepositories()
        given(llmClient.evaluate(question, criteria, answer)).willThrow(LlmEvaluationFailedException())

        // when
        val action = { service.evaluate(questionId, answer) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(LlmEvaluationFailedException::class.java)
        verify(llmClient).evaluate(question, criteria, answer)
        verifyNoMoreInteractions(llmClient)
    }

    @Test
    fun `질문이 없으면 평가 기준 조회와 LLM 호출 없이 질문 없음 예외를 던진다`() {
        // given
        given(questionRepository.findById(questionId)).willReturn(Optional.empty())

        // when
        val action = { service.evaluate(questionId, answer) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(QuestionNotFoundException::class.java)
        verifyNoInteractions(criterionRepository, llmClient)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `DB 조회 실패는 LLM 호출 없이 원래 예외를 전달한다`(questionFails: Boolean) {
        // given
        val failure = IllegalStateException("database unavailable")
        if (questionFails) {
            given(questionRepository.findById(questionId)).willThrow(failure)
        } else {
            given(questionRepository.findById(questionId)).willReturn(Optional.of(question))
            given(criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)).willThrow(failure)
        }

        // when
        val exception = catchThrowable { service.evaluate(questionId, answer) }

        // then
        assertThat(exception).isSameAs(failure)
        verifyNoInteractions(llmClient)
    }

    private fun givenRepositories() {
        given(questionRepository.findById(questionId)).willReturn(Optional.of(question))
        given(criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)).willReturn(criteria)
    }
}
