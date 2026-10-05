package com.eoehd1ek.tech.evaluation

import com.eoehd1ek.tech.evaluation.application.EvaluationCriterionSpec
import com.eoehd1ek.tech.evaluation.application.EvaluationService
import com.eoehd1ek.tech.evaluation.application.exception.LlmEvaluationFailedException
import com.eoehd1ek.tech.evaluation.domain.EvaluationAttempt
import com.eoehd1ek.tech.evaluation.domain.exception.EvaluationAttemptNotFoundException
import com.eoehd1ek.tech.evaluation.domain.EvaluationResult
import com.eoehd1ek.tech.evaluation.infrastructure.llm.EvaluationResponseValidator
import com.eoehd1ek.tech.evaluation.infrastructure.llm.LlmEvaluationClient
import com.eoehd1ek.tech.evaluation.infrastructure.persistence.EvaluationAttemptRepository
import com.eoehd1ek.tech.evaluation.presentation.request.EvaluationPreviewRequest
import com.eoehd1ek.tech.evaluation.presentation.response.EvaluationAttemptResponse
import com.eoehd1ek.tech.evaluation.presentation.response.EvaluationPreviewResponse
import com.eoehd1ek.tech.question.domain.EvaluationCriterion
import com.eoehd1ek.tech.question.infrastructure.persistence.EvaluationCriterionRepository
import com.eoehd1ek.tech.question.domain.Question
import com.eoehd1ek.tech.question.application.exception.QuestionNotFoundException
import com.eoehd1ek.tech.question.infrastructure.persistence.QuestionRepository
import com.eoehd1ek.tech.question.presentation.request.AdminCriterionRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.BDDMockito.given
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.any
import org.mockito.Mockito.times
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class EvaluationServiceTest {
    @Mock
    private lateinit var questionRepository: QuestionRepository

    @Mock
    private lateinit var criterionRepository: EvaluationCriterionRepository

    @Mock
    private lateinit var llmClient: LlmEvaluationClient

    @Mock
    private lateinit var attemptRepository: EvaluationAttemptRepository

    private lateinit var service: EvaluationService

    @BeforeEach
    fun setUp() {
        service = EvaluationService(
            questionRepository,
            criterionRepository,
            llmClient,
            EvaluationResponseValidator(),
            attemptRepository
        )
    }

    private val questionId = 10L
    private val answer = "  테스트 답변\n원문  "
    private val question = Question("테스트 질문", "테스트 본문")
    private val criteria = listOf(EvaluationCriterion(questionId, "테스트 기준", 100, 1).apply {
        ReflectionTestUtils.setField(this, "id", 20L)
    })
    private val input = criteria.map(EvaluationCriterionSpec::from)

    @Test
    fun `질문과 기준을 한번 조회하고 완료 결과를 저장하여 반환 ID와 감사 시각을 응답한다`() {
        // given
        givenRepositories()
        given(llmClient.evaluate(question.title, question.content, input, answer)).willReturn("""
            {"criteria":[{"criterionId":20,"score":85,"feedback":"잘 설명했습니다."}],
             "strengths":"장점", "weaknesses":"단점", "improvements":"개선점"}
        """.trimIndent())
        val saved = savedAttempt()
        given(attemptRepository.save(any(EvaluationAttempt::class.java))).willReturn(saved)

        // when
        val result = service.submit(questionId, answer)

        // then
        assertThat(result).isEqualTo(
            EvaluationAttemptResponse(
                42L, questionId, question.title, answer, 85, EvaluationResult.PASS,
                "장점", "단점", "개선점", requireNotNull(saved.createdAt),
            )
        )
        val captor = ArgumentCaptor.forClass(EvaluationAttempt::class.java)
        verify(attemptRepository).save(captor.capture())
        assertThat(captor.value).usingRecursiveComparison()
            .ignoringFields("id", "createdAt").isEqualTo(saved)
        assertThat(captor.value.id).isNull()
        assertThat(captor.value.createdAt).isNull()
        verify(questionRepository).findById(questionId)
        verify(criterionRepository).findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)
        verify(llmClient).evaluate(question.title, question.content, input, answer)
        verifyNoMoreInteractions(questionRepository, criterionRepository, llmClient, attemptRepository)
    }

    @ParameterizedTest
    @ValueSource(strings = ["not JSON", "{}"])
    fun `잘못된 LLM 응답은 재호출 없이 안전한 평가 실패로 처리한다`(content: String) {
        // given
        givenRepositories()
        given(llmClient.evaluate(question.title, question.content, input, answer)).willReturn(content)

        // when
        val action = { service.submit(questionId, answer) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(LlmEvaluationFailedException::class.java).hasNoCause()
        verify(llmClient).evaluate(question.title, question.content, input, answer)
        verifyNoMoreInteractions(llmClient)
        verifyNoInteractions(attemptRepository)
    }

    @Test
    fun `LLM 호출 실패는 애플리케이션 재호출 없이 전달한다`() {
        // given
        givenRepositories()
        given(llmClient.evaluate(question.title, question.content, input, answer)).willThrow(
            LlmEvaluationFailedException()
        )

        // when
        val action = { service.submit(questionId, answer) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(LlmEvaluationFailedException::class.java)
        verify(llmClient).evaluate(question.title, question.content, input, answer)
        verifyNoMoreInteractions(llmClient)
        verifyNoInteractions(attemptRepository)
    }

    @Test
    fun `질문이 없으면 평가 기준 조회와 LLM 호출 없이 질문 없음 예외를 던진다`() {
        // given
        given(questionRepository.findById(questionId)).willReturn(Optional.empty())

        // when
        val action = { service.submit(questionId, answer) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(QuestionNotFoundException::class.java)
        verifyNoInteractions(criterionRepository, llmClient, attemptRepository)
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
        val exception = catchThrowable { service.submit(questionId, answer) }

        // then
        assertThat(exception).isSameAs(failure)
        verifyNoInteractions(llmClient, attemptRepository)
        if (questionFails) verifyNoInteractions(criterionRepository)
    }

    @Test
    fun `같은 질문과 답변을 재제출하면 각각 평가하고 새로운 기록을 저장한다`() {
        // given
        givenRepositories()
        givenValidEvaluation()
        given(attemptRepository.save(any(EvaluationAttempt::class.java)))
            .willReturn(savedAttempt(42L), savedAttempt(43L))

        // when
        val first = service.submit(questionId, answer)
        val second = service.submit(questionId, answer)

        // then
        assertThat(listOf(first.id, second.id)).containsExactly(42L, 43L)
        val captor = ArgumentCaptor.forClass(EvaluationAttempt::class.java)
        verify(attemptRepository, times(2)).save(captor.capture())
        assertThat(captor.allValues[0]).isNotSameAs(captor.allValues[1])
        verify(questionRepository, times(2)).findById(questionId)
        verify(criterionRepository, times(2)).findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)
        verify(llmClient, times(2)).evaluate(question.title, question.content, input, answer)
    }

    @Test
    fun `저장 실패는 성공 결과 없이 예외를 전달하고 LLM을 재호출하지 않는다`() {
        // given
        givenRepositories()
        givenValidEvaluation()
        val failure = IllegalStateException("private database detail")
        given(attemptRepository.save(any(EvaluationAttempt::class.java))).willThrow(failure)

        // when
        val exception = catchThrowable { service.submit(questionId, answer) }

        // then
        assertThat(exception).isSameAs(failure)
        verify(attemptRepository).save(any(EvaluationAttempt::class.java))
        verify(llmClient).evaluate(question.title, question.content, input, answer)
        verifyNoMoreInteractions(llmClient, attemptRepository)
    }

    @ParameterizedTest
    @ValueSource(strings = ["id", "createdAt"])
    fun `저장 반환 객체의 필수 영속화 값이 누락되면 서버 오류를 발생시킨다`(field: String) {
        // given
        givenRepositories()
        givenValidEvaluation()
        val saved = savedAttempt().apply { ReflectionTestUtils.setField(this, field, null) }
        given(attemptRepository.save(any(EvaluationAttempt::class.java))).willReturn(saved)

        // when
        val action = { service.submit(questionId, answer) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(IllegalStateException::class.java)
        verify(llmClient).evaluate(question.title, question.content, input, answer)
        verifyNoMoreInteractions(llmClient)
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
            EvaluationAttemptResponse(
                42L, questionId, currentQuestion.title, answer, 7, EvaluationResult.PASS,
                saved.strengths, saved.weaknesses, saved.improvements, requireNotNull(saved.createdAt),
            )
        )
        verify(attemptRepository).findById(42L)
        verify(questionRepository).findById(questionId)
        verifyNoMoreInteractions(attemptRepository, questionRepository)
        verifyNoInteractions(criterionRepository, llmClient)
    }

    @Test
    fun `없는 평가 기록 조회는 질문 조회와 평가 없이 기록 없음 예외를 던진다`() {
        // given
        given(attemptRepository.findById(42L)).willReturn(Optional.empty())

        // when
        val action = { service.getAttempt(42L) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(EvaluationAttemptNotFoundException::class.java)
        verifyNoInteractions(questionRepository, criterionRepository, llmClient)
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
        verifyNoInteractions(criterionRepository, llmClient)
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
        verifyNoInteractions(criterionRepository, llmClient)
        if (attemptFails) verifyNoInteractions(questionRepository)
    }

    private fun givenValidEvaluation() {
        given(llmClient.evaluate(question.title, question.content, input, answer)).willReturn("""
            {"criteria":[{"criterionId":20,"score":85,"feedback":"잘 설명했습니다."}],
             "strengths":"장점", "weaknesses":"단점", "improvements":"개선점"}
        """.trimIndent())
    }

    @Test
    fun `저장 전 평가는 지정 순서의 현재 입력으로 평가하고 Repository를 사용하지 않는다`() {
        // given
        val request = EvaluationPreviewRequest(
            " 미저장 제목 ", "미저장 본문", listOf(
                AdminCriterionRequest("나중 기준", 60, 20), AdminCriterionRequest("먼저 기준", 40, -5),
            ), answer
        )
        val previewCriteria = listOf(
            EvaluationCriterionSpec(1L, "먼저 기준", 40), EvaluationCriterionSpec(2L, "나중 기준", 60),
        )
        given(llmClient.evaluate(request.title, request.content, previewCriteria, answer)).willReturn("""
            {"criteria":[{"criterionId":2,"score":50,"feedback":"두번째"},
                         {"criterionId":1,"score":30,"feedback":"첫번째"}],
             "strengths":"장점", "weaknesses":"단점", "improvements":"개선점"}
        """.trimIndent())

        // when
        val result = service.preview(request)

        // then
        assertThat(result).isEqualTo(
            EvaluationPreviewResponse(
                request.title, answer, 80, EvaluationResult.PASS, "장점", "단점", "개선점",
            )
        )
        verify(llmClient).evaluate(request.title, request.content, previewCriteria, answer)
        verifyNoInteractions(questionRepository, criterionRepository, attemptRepository)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `저장 전 평가의 모델 오류와 잘못된 응답은 저장 없이 평가 실패로 처리한다`(callFails: Boolean) {
        // given
        val request = EvaluationPreviewRequest("제목", "본문", listOf(AdminCriterionRequest("기준", 100, 0)), answer)
        val criteria = listOf(EvaluationCriterionSpec(1L, "기준", 100))
        if (callFails) {
            given(llmClient.evaluate(request.title, request.content, criteria, answer))
                .willThrow(LlmEvaluationFailedException())
        } else {
            given(llmClient.evaluate(request.title, request.content, criteria, answer)).willReturn("{}")
        }

        // when
        val action = { service.preview(request) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(LlmEvaluationFailedException::class.java)
        verify(llmClient).evaluate(request.title, request.content, criteria, answer)
        verifyNoMoreInteractions(llmClient)
        verifyNoInteractions(questionRepository, criterionRepository, attemptRepository)
    }

    private fun givenRepositories() {
        given(questionRepository.findById(questionId)).willReturn(Optional.of(question))
        given(criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)).willReturn(criteria)
    }
}
