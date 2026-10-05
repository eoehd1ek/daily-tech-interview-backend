package com.eoehd1ek.tech.question

import jakarta.persistence.*

@Entity
@Table(name = "question")
class Question(
    title: String,
    content: String,
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @field:Column(nullable = false)
    var title: String = title
        protected set

    @field:Column(nullable = false)
    var content: String = content
        protected set


    fun changeTitle(title: String) {
        this.title = title
    }

    fun changeContent(content: String) {
        this.content = content
    }
}
