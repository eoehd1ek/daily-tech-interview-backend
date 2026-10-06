package com.eoehd1ek.tech.question.application

import com.eoehd1ek.tech.question.application.exception.QuestionNotFoundException
import com.eoehd1ek.tech.question.application.result.AdminQuestionDetailResult
import com.eoehd1ek.tech.question.domain.EvaluationCriterion
import com.eoehd1ek.tech.question.domain.Question
import com.eoehd1ek.tech.question.infrastructure.persistence.EvaluationCriterionRepository
import com.eoehd1ek.tech.question.infrastructure.persistence.QuestionRepository
import com.eoehd1ek.tech.question.presentation.request.AdminCriterionRequest
import com.eoehd1ek.tech.question.presentation.request.AdminQuestionRequest
import com.eoehd1ek.tech.question.presentation.response.AdminQuestionResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.BDDMockito.given
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class AdminQuestionServiceTest {
    @Mock
    private lateinit var questionRepository: QuestionRepository

    @Mock
    private lateinit var criterionRepository: EvaluationCriterionRepository

    @InjectMocks
    private lateinit var service: AdminQuestionService

    @Captor
    private lateinit var questionCaptor: ArgumentCaptor<Question>

    @Captor
    private lateinit var criteriaCaptor: ArgumentCaptor<List<EvaluationCriterion>>

    @Test
    fun `관리자 상세 조회는 질문과 해당 기준을 정렬 조회하여 반환한다`() {
        // given
        val question = Question("제목", "본문")
        ReflectionTestUtils.setField(question, "id", 10L)
        val criteria = listOf(
            EvaluationCriterion(10L, "첫 기준", 40, 1),
            EvaluationCriterion(10L, "둘째 기준", 60, 2),
        )
        given(questionRepository.findById(10L)).willReturn(Optional.of(question))
        given(criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(10L)).willReturn(criteria)

        // when
        val result = service.getQuestion(10L)

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(AdminQuestionDetailResult(question, criteria))
        verify(questionRepository).findById(10L)
        verify(criterionRepository).findAllByQuestionIdOrderByDisplayOrderAscIdAsc(10L)
    }

    @Test
    fun `없는 관리자 상세 조회는 기준을 조회하지 않고 질문 없음 예외를 반환한다`() {
        // given
        given(questionRepository.findById(10L)).willReturn(Optional.empty())

        // when
        val action = { service.getQuestion(10L) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(QuestionNotFoundException::class.java)
        verifyNoInteractions(criterionRepository)
    }

    @Test
    fun `질문 생성은 원문과 지정한 순서를 저장하고 순서대로 응답한다`() {
        // given
        val request = AdminQuestionRequest(" 제목 ", "본문\n원문", listOf(
            AdminCriterionRequest("첫 기준", 40, 20), AdminCriterionRequest("둘째 기준", 60, -5),
        ))
        val question = Question(request.title, request.content)
        ReflectionTestUtils.setField(question, "id", 10L)
        val criteria = listOf(
            EvaluationCriterion(10L, "첫 기준", 40, 20),
            EvaluationCriterion(10L, "둘째 기준", 60, -5),
        )
        criteria.forEachIndexed { index, criterion ->
            ReflectionTestUtils.setField(criterion, "id", 20L + index)
        }
        given(questionRepository.save(any(Question::class.java))).willReturn(question)
        given(criterionRepository.saveAll(anyList())).willReturn(criteria)

        // when
        val result = service.create(request)

        // then
        verify(questionRepository).save(questionCaptor.capture())
        assertThat(questionCaptor.value.title).isEqualTo(request.title)
        assertThat(questionCaptor.value.content).isEqualTo(request.content)
        verify(criterionRepository).saveAll(criteriaCaptor.capture())
        assertThat(criteriaCaptor.value).usingRecursiveComparison().ignoringFields("id").isEqualTo(criteria)
        assertThat(result).isEqualTo(AdminQuestionResponse.from(question, criteria))
        assertThat(result.criteria.map { it.displayOrder }).containsExactly(-5, 20)
    }

    @Test
    fun `질문 수정은 ID를 유지하고 제목 본문과 기준 전체를 교체한다`() {
        // given
        val question = Question("이전 제목", "이전 본문")
        ReflectionTestUtils.setField(question, "id", 10L)
        val request = AdminQuestionRequest("새 제목", "새 본문", listOf(AdminCriterionRequest("새 기준", 100, 0)))
        val criterion = EvaluationCriterion(10L, "새 기준", 100, 0)
        ReflectionTestUtils.setField(criterion, "id", 30L)
        given(questionRepository.findById(10L)).willReturn(Optional.of(question))
        given(criterionRepository.saveAll(anyList())).willReturn(listOf(criterion))

        // when
        val result = service.update(10L, request)

        // then
        verify(criterionRepository).deleteAllByQuestionId(10L)
        verify(criterionRepository).saveAll(criteriaCaptor.capture())
        assertThat(criteriaCaptor.value).usingRecursiveComparison().ignoringFields("id")
            .isEqualTo(listOf(criterion))
        assertThat(question.id).isEqualTo(10L)
        assertThat(question.title).isEqualTo(request.title)
        assertThat(question.content).isEqualTo(request.content)
        assertThat(result).isEqualTo(AdminQuestionResponse.from(question, listOf(criterion)))
    }

    @Test
    fun `없는 질문 수정은 기준을 변경하지 않고 질문 없음 예외를 반환한다`() {
        // given
        val request = AdminQuestionRequest("제목", "본문", listOf(AdminCriterionRequest("기준", 100, 1)))
        given(questionRepository.findById(10L)).willReturn(Optional.empty())

        // when
        val action = { service.update(10L, request) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(QuestionNotFoundException::class.java)
        verifyNoInteractions(criterionRepository)
    }
}
