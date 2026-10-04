package com.eoehd1ek.tech.evaluation

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@Table(name = "evaluation_attempt")
@EntityListeners(AuditingEntityListener::class)
class EvaluationAttempt(
    @field:Column(name = "question_id", nullable = false)
    val questionId: Long,

    @field:Column(nullable = false)
    val answer: String,

    @field:Column(nullable = false)
    val score: Int,

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    val result: EvaluationResult,

    @field:Column(nullable = false)
    val strengths: String,

    @field:Column(nullable = false)
    val weaknesses: String,

    @field:Column(nullable = false)
    val improvements: String,
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @field:CreatedDate
    @field:Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null
        protected set
}
