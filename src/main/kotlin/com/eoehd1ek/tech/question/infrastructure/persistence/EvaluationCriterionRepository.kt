package com.eoehd1ek.tech.question.infrastructure.persistence

import com.eoehd1ek.tech.question.domain.EvaluationCriterion
import org.springframework.data.jpa.repository.JpaRepository

interface EvaluationCriterionRepository : JpaRepository<EvaluationCriterion, Long> {
    fun findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId: Long): List<EvaluationCriterion>

    fun deleteAllByQuestionId(questionId: Long)
}
