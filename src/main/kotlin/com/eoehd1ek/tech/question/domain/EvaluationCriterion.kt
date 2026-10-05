package com.eoehd1ek.tech.question.domain

import jakarta.persistence.*

@Entity
@Table(name = "evaluation_criterion")
class EvaluationCriterion(
    @field:Column(name = "question_id", nullable = false)
    val questionId: Long,

    @field:Column(nullable = false)
    val content: String,

    @field:Column(name = "max_score", nullable = false)
    val maxScore: Int,

    @field:Column(name = "display_order", nullable = false)
    val displayOrder: Int,
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
