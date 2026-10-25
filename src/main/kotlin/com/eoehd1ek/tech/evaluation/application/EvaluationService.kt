package com.eoehd1ek.tech.evaluation.application

import com.eoehd1ek.tech.evaluation.application.model.EvaluationCallContext
import com.eoehd1ek.tech.evaluation.application.model.EvaluationCriterionSpec
import com.eoehd1ek.tech.evaluation.application.port.EvaluationProvider
import com.eoehd1ek.tech.evaluation.application.result.EvaluatedAnswerResult
import com.eoehd1ek.tech.evaluation.application.result.EvaluationAttemptResult
import com.eoehd1ek.tech.evaluation.application.validation.EvaluationProviderResultValidator
import com.eoehd1ek.tech.evaluation.domain.EvaluationAttempt
import com.eoehd1ek.tech.evaluation.domain.EvaluationResult
import com.eoehd1ek.tech.evaluation.domain.exception.EvaluationAttemptNotFoundException
import com.eoehd1ek.tech.evaluation.infrastructure.persistence.EvaluationAttemptRepository
import com.eoehd1ek.tech.llmcalllog.application.LlmCallLogWriter
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallPurpose
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
    private val callLogWriter: LlmCallLogWriter,
) {

    fun submitAnswer(questionId: Long, answer: String): EvaluationAttemptResult {
        val question = findQuestion(questionId)
        val criteria = findCriteria(questionId)

        val evaluation = evaluateAnswer(
            title = question.title,
            content = question.content,
            criteria = criteria,
            answer = answer,
            context = EvaluationCallContext(LlmCallPurpose.ANSWER_EVALUATION, questionId),
        )

        val attempt = saveAttempt(
            questionId = questionId,
            answer = answer,
            evaluation = evaluation.result
        )

        val result = EvaluationAttemptResult.from(
            attempt,
            question.title
        )
        callLogWriter.linkEvaluationAttempt(evaluation.callLogId, result.id)
        return result
    }

    fun previewEvaluation(
        title: String,
        content: String,
        criteria: List<EvaluationCriterionSpec>,
        answer: String,
    ): EvaluatedAnswerResult =
        evaluateAnswer(
            title = title,
            content = content,
            criteria = criteria,
            answer = answer,
            context = EvaluationCallContext(LlmCallPurpose.ADMIN_PREVIEW, null),
        ).result

    @Transactional(readOnly = true)
    fun getAttempt(attemptId: Long): EvaluationAttemptResult {
        val attempt = findAttempt(attemptId)
        val question = findQuestionForAttempt(attempt.questionId)

        return EvaluationAttemptResult.from(
            attempt,
            question.title
        )
    }

    private fun evaluateAnswer(
        title: String,
        content: String,
        criteria: List<EvaluationCriterionSpec>,
        answer: String,
        context: EvaluationCallContext,
    ): EvaluationOutcome {
        val call = evaluationProvider.evaluate(
            title = title,
            content = content,
            criteria = criteria,
            answer = answer,
            context = context,
        )
        val providerResult = call.evaluation

        validator.validate(providerResult, criteria)

        val score = providerResult.criteria.sumOf { it.score }

        val result = EvaluatedAnswerResult(
            score = score,
            result = EvaluationResult.fromScore(score),
            strengths = providerResult.strengths,
            weaknesses = providerResult.weaknesses,
            improvements = providerResult.improvements,
        )
        return EvaluationOutcome(result, call.callLogId)
    }

    private data class EvaluationOutcome(
        val result: EvaluatedAnswerResult,
        val callLogId: Long
    )

    private fun saveAttempt(
        questionId: Long,
        answer: String,
        evaluation: EvaluatedAnswerResult,
    ): EvaluationAttempt {
        return attemptRepository.save(
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
    }

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
