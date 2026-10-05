package com.eoehd1ek.tech.evaluation.domain

enum class EvaluationResult {
    FAIL,
    RETRY,
    PASS;

    companion object {
        fun fromScore(score: Int): EvaluationResult = when {
            score < 50 -> FAIL
            score < 80 -> RETRY
            else -> PASS
        }
    }
}
