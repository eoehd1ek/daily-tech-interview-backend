package com.eoehd1ek.tech.question.application

import com.eoehd1ek.tech.question.application.exception.QuestionNotFoundException
import com.eoehd1ek.tech.question.domain.EvaluationCriterion
import com.eoehd1ek.tech.question.domain.Question
import com.eoehd1ek.tech.question.infrastructure.persistence.EvaluationCriterionRepository
import com.eoehd1ek.tech.question.infrastructure.persistence.QuestionRepository
import com.eoehd1ek.tech.question.presentation.request.AdminQuestionRequest
import com.eoehd1ek.tech.question.presentation.response.AdminQuestionResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminQuestionService(
    private val questionRepository: QuestionRepository,
    private val criterionRepository: EvaluationCriterionRepository,
) {
    @Transactional
    fun create(request: AdminQuestionRequest): AdminQuestionResponse {
        val question = questionRepository.save(Question(request.title, request.content))
        val criteria = saveCriteria(checkNotNull(question.id), request)
        return AdminQuestionResponse.from(question, criteria)
    }

    @Transactional
    fun update(questionId: Long, request: AdminQuestionRequest): AdminQuestionResponse {
        val question = questionRepository.findById(questionId)
            .orElseThrow { QuestionNotFoundException() }
        question.changeTitle(request.title)
        question.changeContent(request.content)
        criterionRepository.deleteAllByQuestionId(questionId)
        val criteria = saveCriteria(questionId, request)
        return AdminQuestionResponse.from(question, criteria)
    }

    private fun saveCriteria(questionId: Long, request: AdminQuestionRequest): List<EvaluationCriterion> =
        criterionRepository.saveAll(request.criteria.map { item ->
            val criterion = requireNotNull(item)
            EvaluationCriterion(questionId, criterion.content, criterion.maxScore, criterion.displayOrder)
        })
}
