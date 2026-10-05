package com.eoehd1ek.tech.evaluation

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

        val content = llmClient.evaluate(question, criteria, answer)
        val evaluated = try {
            validator.validate(content, criteria)
        } catch (_: InvalidLlmResponseException) {
            throw LlmEvaluationFailedException()
        }
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

    @Transactional(readOnly = true)
    fun getAttempt(attemptId: Long): EvaluationAttemptResponse {
        val attempt = attemptRepository.findById(attemptId)
            .orElseThrow { EvaluationAttemptNotFoundException() }
        val question = questionRepository.findById(attempt.questionId)
            .orElseThrow { IllegalStateException("Evaluation attempt references a missing question") }
        return EvaluationAttemptResponse.from(attempt, question.title)
    }
}
