package com.eoehd1ek.tech.evaluation.application

import com.eoehd1ek.tech.evaluation.domain.EvaluationAttempt
import com.eoehd1ek.tech.evaluation.domain.exception.EvaluationAttemptNotFoundException
import com.eoehd1ek.tech.evaluation.infrastructure.persistence.EvaluationAttemptRepository
import com.eoehd1ek.tech.evaluation.infrastructure.llm.EvaluationResponseValidator
import com.eoehd1ek.tech.evaluation.infrastructure.llm.InvalidLlmResponseException
import com.eoehd1ek.tech.evaluation.infrastructure.llm.LlmEvaluationClient
import com.eoehd1ek.tech.evaluation.application.exception.LlmEvaluationFailedException
import com.eoehd1ek.tech.evaluation.application.result.EvaluatedAnswerResult
import com.eoehd1ek.tech.evaluation.presentation.request.EvaluationPreviewRequest
import com.eoehd1ek.tech.evaluation.presentation.response.EvaluationAttemptResponse
import com.eoehd1ek.tech.evaluation.presentation.response.EvaluationPreviewResponse
import com.eoehd1ek.tech.question.EvaluationCriterionRepository
import com.eoehd1ek.tech.question.QuestionNotFoundException
import com.eoehd1ek.tech.question.QuestionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EvaluationService(
    private val questionRepository: QuestionRepository,
    private val criterionRepository: EvaluationCriterionRepository,
    private val llmClient: LlmEvaluationClient,
    private val validator: EvaluationResponseValidator,
    private val attemptRepository: EvaluationAttemptRepository,
) {
    fun submit(questionId: Long, answer: String): EvaluationAttemptResponse {
        val question = questionRepository.findById(questionId)
            .orElseThrow { QuestionNotFoundException() }
        val criteria = criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)
            .map(EvaluationCriterionSpec.Companion::from)
        val evaluated = evaluate(question.title, question.content, criteria, answer)
        val saved = attemptRepository.save(
            EvaluationAttempt(
                questionId = questionId,
                answer = answer,
                score = evaluated.score,
                result = evaluated.result,
                strengths = evaluated.strengths,
                weaknesses = evaluated.weaknesses,
                improvements = evaluated.improvements,
            )
        )
        return EvaluationAttemptResponse.from(saved, question.title)
    }

    fun preview(request: EvaluationPreviewRequest): EvaluationPreviewResponse {
        val criteria = request.criteria.map { requireNotNull(it) }
            .sortedBy { it.displayOrder }
            .mapIndexed { index, criterion ->
                EvaluationCriterionSpec(index + 1L, criterion.content, criterion.maxScore)
            }
        val evaluated = evaluate(request.title, request.content, criteria, request.answer)
        return EvaluationPreviewResponse(
            questionTitle = request.title,
            answer = request.answer,
            score = evaluated.score,
            result = evaluated.result,
            strengths = evaluated.strengths,
            weaknesses = evaluated.weaknesses,
            improvements = evaluated.improvements,
        )
    }

    private fun evaluate(
        title: String,
        content: String,
        criteria: List<EvaluationCriterionSpec>,
        answer: String,
    ): EvaluatedAnswerResult {
        val response = llmClient.evaluate(title, content, criteria, answer)
        return try {
            validator.validate(response, criteria)
        } catch (_: InvalidLlmResponseException) {
            throw LlmEvaluationFailedException()
        }
    }

    @Transactional(readOnly = true)
    fun getAttempt(attemptId: Long): EvaluationAttemptResponse {
        val attempt = attemptRepository.findById(attemptId)
            .orElseThrow { EvaluationAttemptNotFoundException() }
        val question = questionRepository.findById(attempt.questionId)
            .orElseThrow { IllegalStateException("Evaluation attempt references a missing question") }
        return EvaluationAttemptResponse.from(attempt, question.title)
    }
}
