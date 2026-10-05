package com.eoehd1ek.tech.evaluation

import com.eoehd1ek.tech.question.EvaluationCriterionRepository
import com.eoehd1ek.tech.question.QuestionNotFoundException
import com.eoehd1ek.tech.question.QuestionRepository
import org.springframework.stereotype.Service

@Service
class EvaluationService(
    private val questionRepository: QuestionRepository,
    private val criterionRepository: EvaluationCriterionRepository,
    private val llmClient: LlmEvaluationClient,
    private val validator: EvaluationResponseValidator,
) {
    fun evaluate(questionId: Long, answer: String): EvaluatedAnswer {
        val question = questionRepository.findById(questionId)
            .orElseThrow { QuestionNotFoundException() }
        val criteria = criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)

        val content = llmClient.evaluate(question, criteria, answer)
        return try {
            validator.validate(content, criteria)
        } catch (_: InvalidLlmResponseException) {
            throw LlmEvaluationFailedException()
        }
    }
}
