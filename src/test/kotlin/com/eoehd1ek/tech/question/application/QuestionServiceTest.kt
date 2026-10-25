package com.eoehd1ek.tech.question.application

import com.eoehd1ek.tech.question.application.exception.QuestionNotFoundException
import com.eoehd1ek.tech.question.domain.Question
import com.eoehd1ek.tech.question.domain.QuestionCategory
import com.eoehd1ek.tech.question.domain.QuestionCategoryAssignment
import com.eoehd1ek.tech.question.infrastructure.persistence.QuestionCategoryAssignmentRepository
import com.eoehd1ek.tech.question.infrastructure.persistence.QuestionRepository
import com.eoehd1ek.tech.question.presentation.response.QuestionDetailResponse
import com.eoehd1ek.tech.question.presentation.response.QuestionResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class QuestionServiceTest {
    @Mock
    private lateinit var questionRepository: QuestionRepository

    @Mock
    private lateinit var categoryRepository: QuestionCategoryAssignmentRepository

    @InjectMocks
    private lateinit var questionService: QuestionService

    @Test
    fun `질문 목록은 카테고리를 일괄 조회하여 미분류와 단일 및 복수 분류를 enum 순서로 반환한다`() {
        // given
        val first = Question("첫 번째 질문", "공개하지 않을 본문")
        val second = Question("두 번째 질문", "다른 질문 본문")
        val third = Question("미분류 질문", "미분류 본문")
        val fourth = Question("프론트엔드 질문", "본문")
        ReflectionTestUtils.setField(first, "id", 10L)
        ReflectionTestUtils.setField(second, "id", 20L)
        ReflectionTestUtils.setField(third, "id", 30L)
        ReflectionTestUtils.setField(fourth, "id", 40L)
        given(questionRepository.findAll()).willReturn(listOf(first, second, third, fourth))
        given(categoryRepository.findAllByQuestionIdIn(listOf(10L, 20L, 30L, 40L))).willReturn(listOf(
            QuestionCategoryAssignment(20L, QuestionCategory.BACKEND),
            QuestionCategoryAssignment(10L, QuestionCategory.BACKEND),
            QuestionCategoryAssignment(40L, QuestionCategory.FRONTEND),
            QuestionCategoryAssignment(10L, QuestionCategory.FRONTEND),
        ))

        // when
        val result = questionService.getQuestions()

        // then
        assertThat(result).containsExactlyInAnyOrder(
            QuestionResponse(10L, first.title, listOf(QuestionCategory.FRONTEND, QuestionCategory.BACKEND)),
            QuestionResponse(20L, second.title, listOf(QuestionCategory.BACKEND)),
            QuestionResponse(30L, third.title, emptyList()),
            QuestionResponse(40L, fourth.title, listOf(QuestionCategory.FRONTEND)),
        )
        verify(questionRepository).findAll()
        verify(categoryRepository).findAllByQuestionIdIn(listOf(10L, 20L, 30L, 40L))
        verifyNoMoreInteractions(categoryRepository)
    }

    @Test
    fun `질문이 없으면 카테고리를 조회하지 않고 빈 목록을 반환한다`() {
        // given
        given(questionRepository.findAll()).willReturn(emptyList())

        // when
        val result = questionService.getQuestions()

        // then
        assertThat(result).isEmpty()
        verify(questionRepository).findAll()
        verifyNoInteractions(categoryRepository)
    }

    @Test
    fun `질문 상세는 DB 문자열 정렬과 달리 카테고리를 enum 순서로 반환한다`() {
        // given
        val questionId = 10L
        val question = Question("상세 질문", "상세 질문 본문")
        ReflectionTestUtils.setField(question, "id", questionId)
        given(questionRepository.findById(questionId)).willReturn(Optional.of(question))
        given(categoryRepository.findAllByQuestionId(questionId)).willReturn(listOf(
            QuestionCategoryAssignment(questionId, QuestionCategory.BACKEND),
            QuestionCategoryAssignment(questionId, QuestionCategory.FRONTEND),
        ))

        // when
        val result = questionService.getQuestion(questionId)

        // then
        assertThat(result).isEqualTo(QuestionDetailResponse(questionId, question.title, question.content,
            listOf(QuestionCategory.FRONTEND, QuestionCategory.BACKEND)))
        verify(questionRepository).findById(questionId)
        verify(categoryRepository).findAllByQuestionId(questionId)
        verifyNoMoreInteractions(categoryRepository)
    }

    @Test
    fun `카테고리가 없는 질문도 상세와 빈 카테고리 배열을 반환한다`() {
        // given
        val questionId = 10L
        val question = Question("미분류 질문", "질문 본문")
        ReflectionTestUtils.setField(question, "id", questionId)
        given(questionRepository.findById(questionId)).willReturn(Optional.of(question))
        given(categoryRepository.findAllByQuestionId(questionId)).willReturn(emptyList())

        // when
        val result = questionService.getQuestion(questionId)

        // then
        assertThat(result).isEqualTo(QuestionDetailResponse(questionId, question.title, question.content, emptyList()))
        verify(categoryRepository).findAllByQuestionId(questionId)
    }

    @Test
    fun `존재하지 않는 질문을 조회하면 질문 없음 예외가 발생한다`() {
        // given
        val questionId = 10L
        given(questionRepository.findById(questionId)).willReturn(Optional.empty())

        // when
        val action = { questionService.getQuestion(questionId) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(QuestionNotFoundException::class.java)
        verify(questionRepository).findById(questionId)
        verifyNoInteractions(categoryRepository)
    }
}
