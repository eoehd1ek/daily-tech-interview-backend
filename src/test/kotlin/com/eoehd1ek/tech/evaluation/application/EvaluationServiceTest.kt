package com.eoehd1ek.tech.evaluation.application

import com.eoehd1ek.tech.evaluation.application.exception.LlmEvaluationFailedException
import com.eoehd1ek.tech.evaluation.application.model.EvaluationCallContext
import com.eoehd1ek.tech.evaluation.application.model.EvaluationCallResult
import com.eoehd1ek.tech.evaluation.application.model.EvaluationCriterionSpec
import com.eoehd1ek.tech.evaluation.application.model.EvaluationProviderResult
import com.eoehd1ek.tech.evaluation.application.port.EvaluationProvider
import com.eoehd1ek.tech.evaluation.application.result.EvaluatedAnswerResult
import com.eoehd1ek.tech.evaluation.application.result.EvaluationAttemptResult
import com.eoehd1ek.tech.evaluation.application.validation.EvaluationProviderResultValidator
import com.eoehd1ek.tech.evaluation.domain.EvaluationAttempt
import com.eoehd1ek.tech.evaluation.domain.EvaluationResult
import com.eoehd1ek.tech.evaluation.domain.exception.EvaluationAttemptNotFoundException
import com.eoehd1ek.tech.evaluation.infrastructure.persistence.EvaluationAttemptRepository
import com.eoehd1ek.tech.llmcalllog.application.LlmCallLogWriter
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallPurpose
import com.eoehd1ek.tech.question.application.exception.QuestionNotFoundException
import com.eoehd1ek.tech.question.domain.EvaluationCriterion
import com.eoehd1ek.tech.question.domain.Question
import com.eoehd1ek.tech.question.infrastructure.persistence.EvaluationCriterionRepository
import com.eoehd1ek.tech.question.infrastructure.persistence.QuestionRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class EvaluationServiceTest {
    @Mock
    private lateinit var questionRepository: QuestionRepository

    @Mock
    private lateinit var criterionRepository: EvaluationCriterionRepository

    @Mock
    private lateinit var llmClient: EvaluationProvider

    @Mock
    private lateinit var attemptRepository: EvaluationAttemptRepository

    @Mock
    private lateinit var callLogWriter: LlmCallLogWriter

    private lateinit var service: EvaluationService

    @BeforeEach
    fun setUp() {
        service = EvaluationService(
            questionRepository,
            criterionRepository,
            llmClient,
            EvaluationProviderResultValidator(),
            attemptRepository,
            callLogWriter,
        )
    }

    private val questionId = 10L
    private val answerContext = EvaluationCallContext(LlmCallPurpose.ANSWER_EVALUATION, questionId)
    private val previewContext = EvaluationCallContext(LlmCallPurpose.ADMIN_PREVIEW, null)
    private val answer = "  테스트 답변\n원문  "
    private val question = Question("테스트 질문", "테스트 본문")
    private val criteria = listOf(EvaluationCriterion(questionId, "테스트 기준", 100, 1).apply {
        ReflectionTestUtils.setField(this, "id", 20L)
    })
    private val input = criteria.map(EvaluationCriterionSpec::from)

    @Test
    fun `질문과 기준을 한번 조회하고 완료 결과 저장 후 호출 로그를 연결하여 ID와 감사 시각을 응답한다`() {
        // given
        givenRepositories()
        givenValidEvaluation()
        val saved = savedAttempt()
        given(attemptRepository.save(any(EvaluationAttempt::class.java))).willReturn(saved)

        // when
        val result = service.submitAnswer(questionId, answer)

        // then
        assertThat(result).isEqualTo(
            EvaluationAttemptResult(
                42L, questionId, question.title, answer, 85, EvaluationResult.PASS,
                "장점", "단점", "개선점", requireNotNull(saved.createdAt),
            )
        )
        val captor = ArgumentCaptor.forClass(EvaluationAttempt::class.java)
        val order = inOrder(attemptRepository, callLogWriter)
        order.verify(attemptRepository).save(captor.capture())
        order.verify(callLogWriter).linkEvaluationAttempt(100L, 42L)
        assertThat(captor.value).usingRecursiveComparison()
            .ignoringFields("id", "createdAt").isEqualTo(saved)
        assertThat(captor.value.id).isNull()
        assertThat(captor.value.createdAt).isNull()
        verify(questionRepository).findById(questionId)
        verify(criterionRepository).findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)
        verify(llmClient).evaluate(question.title, question.content, input, answer, answerContext)
        verifyNoMoreInteractions(questionRepository, criterionRepository, llmClient, attemptRepository, callLogWriter)
    }

    @Test
    fun `기준과 불일치하는 평가 응답은 재호출 없이 안전한 평가 실패로 처리한다`() {
        // given
        givenRepositories()
        given(llmClient.evaluate(question.title, question.content, input, answer, answerContext)).willReturn(
            EvaluationCallResult(
                EvaluationProviderResult(
                    listOf(EvaluationProviderResult.CriterionResult(21L, 85, "피드백")),
                    "장점", "단점", "개선점"
                ),
                100L,
            ),
        )

        // when
        val action = { service.submitAnswer(questionId, answer) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(LlmEvaluationFailedException::class.java).hasNoCause()
        verify(llmClient).evaluate(question.title, question.content, input, answer, answerContext)
        verifyNoMoreInteractions(llmClient)
        verifyNoInteractions(attemptRepository, callLogWriter)
    }

    @Test
    fun `LLM 호출 실패는 애플리케이션 재호출 없이 전달한다`() {
        // given
        givenRepositories()
        given(llmClient.evaluate(question.title, question.content, input, answer, answerContext)).willThrow(
            LlmEvaluationFailedException()
        )

        // when
        val action = { service.submitAnswer(questionId, answer) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(LlmEvaluationFailedException::class.java)
        verify(llmClient).evaluate(question.title, question.content, input, answer, answerContext)
        verifyNoMoreInteractions(llmClient)
        verifyNoInteractions(attemptRepository, callLogWriter)
    }

    @Test
    fun `질문이 없으면 평가 기준 조회와 LLM 호출 없이 질문 없음 예외를 던진다`() {
        // given
        given(questionRepository.findById(questionId)).willReturn(Optional.empty())

        // when
        val action = { service.submitAnswer(questionId, answer) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(QuestionNotFoundException::class.java)
        verifyNoInteractions(criterionRepository, llmClient, attemptRepository, callLogWriter)
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
        val exception = catchThrowable { service.submitAnswer(questionId, answer) }

        // then
        assertThat(exception).isSameAs(failure)
        verifyNoInteractions(llmClient, attemptRepository, callLogWriter)
        if (questionFails) verifyNoInteractions(criterionRepository)
    }

    @Test
    fun `같은 질문과 답변을 재제출하면 각각 평가하고 새로운 기록과 각 호출 로그를 연결한다`() {
        // given
        givenRepositories()
        given(llmClient.evaluate(question.title, question.content, input, answer, answerContext))
            .willReturn(validEvaluation(100L), validEvaluation(101L))
        given(attemptRepository.save(any(EvaluationAttempt::class.java)))
            .willReturn(savedAttempt(42L), savedAttempt(43L))

        // when
        val first = service.submitAnswer(questionId, answer)
        val second = service.submitAnswer(questionId, answer)

        // then
        assertThat(listOf(first.id, second.id)).containsExactly(42L, 43L)
        val captor = ArgumentCaptor.forClass(EvaluationAttempt::class.java)
        verify(attemptRepository, times(2)).save(captor.capture())
        assertThat(captor.allValues[0]).isNotSameAs(captor.allValues[1])
        verify(questionRepository, times(2)).findById(questionId)
        verify(criterionRepository, times(2)).findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)
        verify(llmClient, times(2)).evaluate(question.title, question.content, input, answer, answerContext)
        verify(callLogWriter).linkEvaluationAttempt(100L, 42L)
        verify(callLogWriter).linkEvaluationAttempt(101L, 43L)
        verifyNoMoreInteractions(llmClient, attemptRepository, callLogWriter)
    }

    @Test
    fun `저장 실패는 호출 로그 연결 없이 예외를 전달하고 LLM을 재호출하지 않는다`() {
        // given
        givenRepositories()
        givenValidEvaluation()
        val failure = IllegalStateException("private database detail")
        given(attemptRepository.save(any(EvaluationAttempt::class.java))).willThrow(failure)

        // when
        val exception = catchThrowable { service.submitAnswer(questionId, answer) }

        // then
        assertThat(exception).isSameAs(failure)
        verify(attemptRepository).save(any(EvaluationAttempt::class.java))
        verify(llmClient).evaluate(question.title, question.content, input, answer, answerContext)
        verifyNoMoreInteractions(llmClient, attemptRepository)
        verifyNoInteractions(callLogWriter)
    }

    @ParameterizedTest
    @ValueSource(strings = ["id", "createdAt"])
    fun `저장 반환 객체의 필수 영속화 값이 누락되면 호출 로그 연결 없이 서버 오류를 발생시킨다`(field: String) {
        // given
        givenRepositories()
        givenValidEvaluation()
        val saved = savedAttempt().apply { ReflectionTestUtils.setField(this, field, null) }
        given(attemptRepository.save(any(EvaluationAttempt::class.java))).willReturn(saved)

        // when
        val action = { service.submitAnswer(questionId, answer) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(IllegalStateException::class.java)
        verify(attemptRepository).save(any(EvaluationAttempt::class.java))
        verify(llmClient).evaluate(question.title, question.content, input, answer, answerContext)
        verifyNoMoreInteractions(llmClient, attemptRepository)
        verifyNoInteractions(callLogWriter)
    }

    @Test
    fun `호출 로그 연결 실패는 원래 예외를 전파하고 평가나 저장을 재호출하지 않는다`() {
        // given
        givenRepositories()
        givenValidEvaluation()
        given(attemptRepository.save(any(EvaluationAttempt::class.java))).willReturn(savedAttempt())
        val failure = IllegalStateException("private call log detail")
        willThrow(failure).given(callLogWriter).linkEvaluationAttempt(100L, 42L)

        // when
        val exception = catchThrowable { service.submitAnswer(questionId, answer) }

        // then
        assertThat(exception).isSameAs(failure)
        val order = inOrder(llmClient, attemptRepository, callLogWriter)
        order.verify(llmClient).evaluate(question.title, question.content, input, answer, answerContext)
        order.verify(attemptRepository).save(any(EvaluationAttempt::class.java))
        order.verify(callLogWriter).linkEvaluationAttempt(100L, 42L)
        verifyNoMoreInteractions(llmClient, attemptRepository, callLogWriter)
    }

    private fun savedAttempt(id: Long = 42L) =
        EvaluationAttempt(questionId, answer, 85, EvaluationResult.PASS, "장점", "단점", "개선점").apply {
            ReflectionTestUtils.setField(this, "id", id)
            ReflectionTestUtils.setField(this, "createdAt", Instant.parse("2026-10-01T07:30:00Z"))
        }

    @Test
    fun `평가 기록 조회는 저장된 결과와 현재 질문 제목을 평가 없이 복원한다`() {
        // given
        val saved = EvaluationAttempt(
            questionId, answer, 7, EvaluationResult.PASS,
            "  저장된 장점\n  ", "저장된 단점", "저장된 개선점"
        ).apply {
            ReflectionTestUtils.setField(this, "id", 42L)
            ReflectionTestUtils.setField(this, "createdAt", Instant.parse("2026-10-01T07:30:00Z"))
        }
        val currentQuestion = Question("변경된 현재 제목", "현재 본문")
        given(attemptRepository.findById(42L)).willReturn(Optional.of(saved))
        given(questionRepository.findById(questionId)).willReturn(Optional.of(currentQuestion))

        // when
        val response = service.getAttempt(42L)

        // then
        assertThat(response).isEqualTo(
            EvaluationAttemptResult(
                42L, questionId, currentQuestion.title, answer, 7, EvaluationResult.PASS,
                saved.strengths, saved.weaknesses, saved.improvements, requireNotNull(saved.createdAt),
            )
        )
        verify(attemptRepository).findById(42L)
        verify(questionRepository).findById(questionId)
        verifyNoMoreInteractions(attemptRepository, questionRepository)
        verifyNoInteractions(criterionRepository, llmClient, callLogWriter)
    }

    @Test
    fun `없는 평가 기록 조회는 질문 조회와 평가 없이 기록 없음 예외를 던진다`() {
        // given
        given(attemptRepository.findById(42L)).willReturn(Optional.empty())

        // when
        val action = { service.getAttempt(42L) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(EvaluationAttemptNotFoundException::class.java)
        verifyNoInteractions(questionRepository, criterionRepository, llmClient, callLogWriter)
    }

    @Test
    fun `기록의 참조 질문이 없으면 질문 없음 응답이 아닌 내부 정합성 오류를 발생시킨다`() {
        // given
        given(attemptRepository.findById(42L)).willReturn(Optional.of(savedAttempt()))
        given(questionRepository.findById(questionId)).willReturn(Optional.empty())

        // when
        val action = { service.getAttempt(42L) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(IllegalStateException::class.java)
        verifyNoInteractions(criterionRepository, llmClient, callLogWriter)
        verify(attemptRepository).findById(42L)
        verifyNoMoreInteractions(attemptRepository)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `결과 조회 중 DB 오류는 평가나 저장 없이 전달한다`(attemptFails: Boolean) {
        // given
        val failure = IllegalStateException("private database detail")
        if (attemptFails) {
            given(attemptRepository.findById(42L)).willThrow(failure)
        } else {
            given(attemptRepository.findById(42L)).willReturn(Optional.of(savedAttempt()))
            given(questionRepository.findById(questionId)).willThrow(failure)
        }

        // when
        val exception = catchThrowable { service.getAttempt(42L) }

        // then
        assertThat(exception).isSameAs(failure)
        verify(attemptRepository).findById(42L)
        verifyNoMoreInteractions(attemptRepository)
        verifyNoInteractions(criterionRepository, llmClient, callLogWriter)
        if (attemptFails) verifyNoInteractions(questionRepository)
    }

    private fun givenValidEvaluation() {
        given(llmClient.evaluate(question.title, question.content, input, answer, answerContext))
            .willReturn(validEvaluation())
    }

    private fun validEvaluation(callLogId: Long = 100L) = EvaluationCallResult(
        EvaluationProviderResult(
            listOf(EvaluationProviderResult.CriterionResult(20L, 85, "잘 설명했습니다.")),
            "장점", "단점", "개선점"
        ),
        callLogId,
    )

    @Test
    fun `저장 전 평가는 관리자 미리보기 컨텍스트와 현재 입력으로 평가하고 저장이나 호출 로그 연결을 하지 않는다`() {
        // given
        val title = " 미저장 제목 "
        val content = "미저장 본문"
        val previewCriteria = listOf(
            EvaluationCriterionSpec(20L, "첫번째 기준", 40), EvaluationCriterionSpec(10L, "두번째 기준", 60),
        )
        given(llmClient.evaluate(title, content, previewCriteria, answer, previewContext)).willReturn(
            EvaluationCallResult(
                EvaluationProviderResult(
                    listOf(
                        EvaluationProviderResult.CriterionResult(10L, 50, "두번째"),
                        EvaluationProviderResult.CriterionResult(20L, 30, "첫번째"),
                    ), "장점", "단점", "개선점"
                ),
                100L,
            ),
        )

        // when
        val result = service.previewEvaluation(title, content, previewCriteria, answer)

        // then
        assertThat(result).isEqualTo(
            EvaluatedAnswerResult(
                80, EvaluationResult.PASS, "장점", "단점", "개선점",
            )
        )
        verify(llmClient).evaluate(title, content, previewCriteria, answer, previewContext)
        verifyNoMoreInteractions(llmClient)
        verifyNoInteractions(questionRepository, criterionRepository, attemptRepository, callLogWriter)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `저장 전 평가의 모델 오류와 잘못된 응답은 저장 없이 평가 실패로 처리한다`(callFails: Boolean) {
        // given
        val title = "제목"
        val content = "본문"
        val criteria = listOf(EvaluationCriterionSpec(1L, "기준", 100))
        if (callFails) {
            given(llmClient.evaluate(title, content, criteria, answer, previewContext))
                .willThrow(LlmEvaluationFailedException())
        } else {
            given(llmClient.evaluate(title, content, criteria, answer, previewContext)).willReturn(
                EvaluationCallResult(EvaluationProviderResult(emptyList(), "장점", "단점", "개선점"), 100L),
            )
        }

        // when
        val action = { service.previewEvaluation(title, content, criteria, answer) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(LlmEvaluationFailedException::class.java)
        verify(llmClient).evaluate(title, content, criteria, answer, previewContext)
        verifyNoMoreInteractions(llmClient)
        verifyNoInteractions(questionRepository, criterionRepository, attemptRepository, callLogWriter)
    }

    private fun givenRepositories() {
        given(questionRepository.findById(questionId)).willReturn(Optional.of(question))
        given(criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)).willReturn(criteria)
    }
}
