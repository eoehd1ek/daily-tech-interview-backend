package com.eoehd1ek.tech.question.application

import com.eoehd1ek.tech.question.application.exception.QuestionNotFoundException
import com.eoehd1ek.tech.question.infrastructure.persistence.QuestionCategoryAssignmentRepository
import com.eoehd1ek.tech.question.infrastructure.persistence.QuestionRepository
import com.eoehd1ek.tech.question.presentation.response.QuestionDetailResponse
import com.eoehd1ek.tech.question.presentation.response.QuestionResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QuestionService(
    private val questionRepository: QuestionRepository,
    private val questionCategoryAssignmentRepository: QuestionCategoryAssignmentRepository,
) {

    @Transactional(readOnly = true)
    fun getQuestions(): List<QuestionResponse> {
        val questions = questionRepository.findAll()
        if (questions.isEmpty()) return emptyList()

        val categoriesByQuestionId = questionCategoryAssignmentRepository
            .findAllByQuestionIdIn(questions.map { checkNotNull(it.id) })
            .groupBy { it.questionId }

        return questions.map { question ->
            val questionId = checkNotNull(question.id)
            QuestionResponse(
                id = questionId,
                title = question.title,
                categories = categoriesByQuestionId[questionId].orEmpty()
                    .map { it.category }
                    .sortedBy { it.ordinal },
            )
        }
    }

    @Transactional(readOnly = true)
    fun getQuestion(questionId: Long): QuestionDetailResponse {
        val question = questionRepository.findById(questionId)
            .orElseThrow { QuestionNotFoundException() }
        val categories = questionCategoryAssignmentRepository.findAllByQuestionId(questionId)
            .map { it.category }
            .sortedBy { it.ordinal }

        return QuestionDetailResponse(
            id = checkNotNull(question.id),
            title = question.title,
            content = question.content,
            categories = categories,
        )
    }
}
