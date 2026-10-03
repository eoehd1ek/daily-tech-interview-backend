package com.eoehd1ek.tech.question

import jakarta.persistence.*

@Entity
@Table(name = "question")
class Question(
    @field:Column(nullable = false)
    val title: String,

    @field:Column(nullable = false)
    val content: String,
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
