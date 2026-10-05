package com.eoehd1ek.tech.question.infrastructure.persistence

import com.eoehd1ek.tech.question.domain.Question
import org.springframework.data.jpa.repository.JpaRepository

interface QuestionRepository : JpaRepository<Question, Long>
