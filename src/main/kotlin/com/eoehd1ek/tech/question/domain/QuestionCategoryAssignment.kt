package com.eoehd1ek.tech.question.domain

import jakarta.persistence.*

@Entity
@Table(name = "question_category")
class QuestionCategoryAssignment(
    @field:Column(name = "question_id", nullable = false)
    val questionId: Long,

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    val category: QuestionCategory,
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
