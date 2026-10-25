package com.eoehd1ek.tech.question.application

import com.eoehd1ek.tech.question.application.exception.QuestionNotFoundException
import com.eoehd1ek.tech.question.application.result.AdminQuestionDetailResult
import com.eoehd1ek.tech.question.domain.EvaluationCriterion
import com.eoehd1ek.tech.question.domain.Question
import com.eoehd1ek.tech.question.domain.QuestionCategoryAssignment
import com.eoehd1ek.tech.question.infrastructure.persistence.EvaluationCriterionRepository
import com.eoehd1ek.tech.question.infrastructure.persistence.QuestionCategoryAssignmentRepository
import com.eoehd1ek.tech.question.infrastructure.persistence.QuestionRepository
import com.eoehd1ek.tech.question.presentation.request.AdminQuestionRequest
import com.eoehd1ek.tech.question.presentation.response.AdminQuestionResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminQuestionService(
    private val questionRepository: QuestionRepository,
    private val criterionRepository: EvaluationCriterionRepository,
    private val categoryRepository: QuestionCategoryAssignmentRepository,
) {

    @Transactional(readOnly = true)
    fun getQuestion(questionId: Long): AdminQuestionDetailResult {
        val question = questionRepository.findById(questionId)
            .orElseThrow { QuestionNotFoundException() }
        val criteria = criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)
        val categories = categoryRepository.findAllByQuestionId(questionId).map { it.category }.sortedBy { it.ordinal }
        return AdminQuestionDetailResult(question, criteria, categories)
    }

    @Transactional
    fun create(request: AdminQuestionRequest): AdminQuestionResponse {
        val question = questionRepository.save(Question(request.title, request.content))
        val criteria = saveCriteria(checkNotNull(question.id), request)
        saveCategories(checkNotNull(question.id), request)
        return AdminQuestionResponse.from(question, criteria, request.categories)
    }

    @Transactional
    fun update(questionId: Long, request: AdminQuestionRequest): AdminQuestionResponse {
        val question = questionRepository.findById(questionId)
            .orElseThrow { QuestionNotFoundException() }
        question.changeTitle(request.title)
        question.changeContent(request.content)
        criterionRepository.deleteAllByQuestionId(questionId)
        val criteria = saveCriteria(questionId, request)
        categoryRepository.deleteAllByQuestionId(questionId)
        saveCategories(questionId, request)
        return AdminQuestionResponse.from(question, criteria, request.categories)
    }

    private fun saveCategories(questionId: Long, request: AdminQuestionRequest) {
        if (request.categories.isNotEmpty()) {
            categoryRepository.saveAll(request.categories.map { QuestionCategoryAssignment(questionId, it) })
        }
    }

    private fun saveCriteria(questionId: Long, request: AdminQuestionRequest): List<EvaluationCriterion> {
        return criterionRepository.saveAll(request.criteria.map { item ->
            val criterion = requireNotNull(item)
            EvaluationCriterion(questionId, criterion.content, criterion.maxScore, criterion.displayOrder)
        })
    }
}
