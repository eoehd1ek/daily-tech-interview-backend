package com.eoehd1ek.tech.evaluation.application.result

import com.eoehd1ek.tech.evaluation.domain.EvaluationAttempt
import com.eoehd1ek.tech.evaluation.domain.EvaluationResult
import java.time.Instant

data class EvaluationAttemptResult(
    val id: Long,
    val questionId: Long,
    val questionTitle: String,
    val answer: String,
    val score: Int,
    val result: EvaluationResult,
    val strengths: String,
    val weaknesses: String,
    val improvements: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(attempt: EvaluationAttempt, questionTitle: String): EvaluationAttemptResult =
            EvaluationAttemptResult(
                id = checkNotNull(attempt.id),
                questionId = attempt.questionId,
                questionTitle = questionTitle,
                answer = attempt.answer,
                score = attempt.score,
                result = attempt.result,
                strengths = attempt.strengths,
                weaknesses = attempt.weaknesses,
                improvements = attempt.improvements,
                createdAt = checkNotNull(attempt.createdAt),
            )
    }
}
