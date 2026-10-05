package com.eoehd1ek.tech.question.application

import com.eoehd1ek.tech.question.application.exception.QuestionNotFoundException
import com.eoehd1ek.tech.question.domain.Question
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
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class QuestionServiceTest {
    @Mock
    private lateinit var questionRepository: QuestionRepository

    @InjectMocks
    private lateinit var questionService: QuestionService

    @Test
    fun `질문 목록을 조회하면 모든 질문의 ID와 제목을 반환한다`() {
        // given
        val first = Question("첫 번째 질문", "공개하지 않을 본문")
        val second = Question("두 번째 질문", "다른 질문 본문")
        ReflectionTestUtils.setField(first, "id", 10L)
        ReflectionTestUtils.setField(second, "id", 20L)
        given(questionRepository.findAll()).willReturn(listOf(first, second))

        // when
        val result = questionService.getQuestions()

        // then
        assertThat(result).containsExactlyInAnyOrder(
            QuestionResponse(10L, first.title),
            QuestionResponse(20L, second.title),
        )
        verify(questionRepository).findAll()
    }

    @Test
    fun `질문이 없으면 빈 목록을 반환한다`() {
        // given
        given(questionRepository.findAll()).willReturn(emptyList())

        // when
        val result = questionService.getQuestions()

        // then
        assertThat(result).isEmpty()
        verify(questionRepository).findAll()
    }

    @Test
    fun `질문 상세를 조회하면 질문의 ID와 제목과 본문을 반환한다`() {
        // given
        val questionId = 10L
        val question = Question("상세 질문", "상세 질문 본문")
        ReflectionTestUtils.setField(question, "id", questionId)
        given(questionRepository.findById(questionId)).willReturn(Optional.of(question))

        // when
        val result = questionService.getQuestion(questionId)

        // then
        assertThat(result).isEqualTo(QuestionDetailResponse(questionId, question.title, question.content))
        verify(questionRepository).findById(questionId)
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
    }
}
