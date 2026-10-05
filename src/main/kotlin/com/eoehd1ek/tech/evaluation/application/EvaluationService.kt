package com.eoehd1ek.tech.evaluation.application

import com.eoehd1ek.tech.evaluation.application.model.EvaluationCriterionSpec
import com.eoehd1ek.tech.evaluation.application.port.EvaluationProvider
import com.eoehd1ek.tech.evaluation.application.result.EvaluatedAnswerResult
import com.eoehd1ek.tech.evaluation.application.validation.EvaluationProviderResultValidator
import com.eoehd1ek.tech.evaluation.domain.EvaluationAttempt
import com.eoehd1ek.tech.evaluation.domain.EvaluationResult
import com.eoehd1ek.tech.evaluation.domain.exception.EvaluationAttemptNotFoundException
import com.eoehd1ek.tech.evaluation.infrastructure.persistence.EvaluationAttemptRepository
import com.eoehd1ek.tech.evaluation.presentation.request.EvaluationPreviewRequest
import com.eoehd1ek.tech.evaluation.presentation.response.EvaluationAttemptResponse
import com.eoehd1ek.tech.evaluation.presentation.response.EvaluationPreviewResponse
import com.eoehd1ek.tech.question.application.exception.QuestionNotFoundException
import com.eoehd1ek.tech.question.domain.Question
import com.eoehd1ek.tech.question.infrastructure.persistence.EvaluationCriterionRepository
import com.eoehd1ek.tech.question.infrastructure.persistence.QuestionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EvaluationService(
    private val questionRepository: QuestionRepository,
    private val criterionRepository: EvaluationCriterionRepository,
    private val evaluationProvider: EvaluationProvider,
    private val validator: EvaluationProviderResultValidator,
    private val attemptRepository: EvaluationAttemptRepository,
) {

    fun submit(questionId: Long, answer: String): EvaluationAttemptResponse {
        val question = findQuestion(questionId)
        val criteria = findCriteria(questionId)

        val evaluation = evaluateAnswer(
            title = question.title,
            content = question.content,
            criteria = criteria,
            answer = answer,
        )

        val attempt = saveAttempt(
            questionId = questionId,
            answer = answer,
            evaluation = evaluation
        )

        return EvaluationAttemptResponse.from(
            attempt,
            question.title
        )
    }

    fun preview(request: EvaluationPreviewRequest): EvaluationPreviewResponse {
        val criteria = createPreviewCriteria(request)

        val evaluation = evaluateAnswer(
            title = request.title,
            content = request.content,
            criteria = criteria,
            answer = request.answer,
        )

        return EvaluationPreviewResponse(
            questionTitle = request.title,
            answer = request.answer,
            score = evaluation.score,
            result = evaluation.result,
            strengths = evaluation.strengths,
            weaknesses = evaluation.weaknesses,
            improvements = evaluation.improvements,
        )
    }

    @Transactional(readOnly = true)
    fun getAttempt(attemptId: Long): EvaluationAttemptResponse {
        val attempt = findAttempt(attemptId)
        val question = findQuestionForAttempt(attempt.questionId)

        return EvaluationAttemptResponse.from(
            attempt,
            question.title
        )
    }

    private fun evaluateAnswer(
        title: String,
        content: String,
        criteria: List<EvaluationCriterionSpec>,
        answer: String,
    ): EvaluatedAnswerResult {
        val providerResult = evaluationProvider.evaluate(
            title = title,
            content = content,
            criteria = criteria,
            answer = answer,
        )

        validator.validate(providerResult, criteria)

        val score = providerResult.criteria.sumOf { it.score }

        return EvaluatedAnswerResult(
            score = score,
            result = EvaluationResult.fromScore(score),
            strengths = providerResult.strengths,
            weaknesses = providerResult.weaknesses,
            improvements = providerResult.improvements,
        )
    }

    private fun createPreviewCriteria(
        request: EvaluationPreviewRequest,
    ): List<EvaluationCriterionSpec> =
        request.criteria
            .map { requireNotNull(it) }
            .sortedBy { it.displayOrder }
            .mapIndexed { index, criterion ->
                EvaluationCriterionSpec(
                    id = index + 1L,
                    content = criterion.content,
                    maxScore = criterion.maxScore,
                )
            }


    private fun saveAttempt(
        questionId: Long,
        answer: String,
        evaluation: EvaluatedAnswerResult,
    ): EvaluationAttempt =
        attemptRepository.save(
            EvaluationAttempt(
                questionId = questionId,
                answer = answer,
                score = evaluation.score,
                result = evaluation.result,
                strengths = evaluation.strengths,
                weaknesses = evaluation.weaknesses,
                improvements = evaluation.improvements,
            )
        )


    private fun findQuestion(questionId: Long): Question =
        questionRepository.findById(questionId)
            .orElseThrow { QuestionNotFoundException() }

    private fun findCriteria(questionId: Long): List<EvaluationCriterionSpec> =
        criterionRepository
            .findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)
            .map(EvaluationCriterionSpec.Companion::from)

    private fun findAttempt(attemptId: Long): EvaluationAttempt =
        attemptRepository.findById(attemptId)
            .orElseThrow { EvaluationAttemptNotFoundException() }

    private fun findQuestionForAttempt(questionId: Long): Question =
        questionRepository.findById(questionId)
            .orElseThrow {
                IllegalStateException(
                    "Evaluation attempt references missing question: questionId=$questionId"
                )
            }
}
