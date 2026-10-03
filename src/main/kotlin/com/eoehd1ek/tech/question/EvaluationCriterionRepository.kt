package com.eoehd1ek.tech.question

import org.springframework.data.jpa.repository.JpaRepository

interface EvaluationCriterionRepository : JpaRepository<EvaluationCriterion, Long> {
    fun findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId: Long): List<EvaluationCriterion>
}
