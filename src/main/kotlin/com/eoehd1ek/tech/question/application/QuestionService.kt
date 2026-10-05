package com.eoehd1ek.tech.question.application

import com.eoehd1ek.tech.question.application.exception.QuestionNotFoundException
import com.eoehd1ek.tech.question.infrastructure.persistence.QuestionRepository
import com.eoehd1ek.tech.question.presentation.response.QuestionDetailResponse
import com.eoehd1ek.tech.question.presentation.response.QuestionResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QuestionService(
    private val questionRepository: QuestionRepository,
) {
    @Transactional(readOnly = true)
    fun getQuestions(): List<QuestionResponse> =
        questionRepository.findAll().map { question ->
            QuestionResponse(
                id = checkNotNull(question.id),
                title = question.title,
            )
        }

    @Transactional(readOnly = true)
    fun getQuestion(questionId: Long): QuestionDetailResponse {
        val question = questionRepository.findById(questionId)
            .orElseThrow { QuestionNotFoundException() }

        return QuestionDetailResponse(
            id = checkNotNull(question.id),
            title = question.title,
            content = question.content,
        )
    }
}
