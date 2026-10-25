package com.eoehd1ek.tech.question.infrastructure.persistence

import com.eoehd1ek.tech.question.domain.QuestionCategoryAssignment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface QuestionCategoryAssignmentRepository : JpaRepository<QuestionCategoryAssignment, Long> {

    fun findAllByQuestionIdIn(questionIds: Collection<Long>): List<QuestionCategoryAssignment>

    fun findAllByQuestionId(questionId: Long): List<QuestionCategoryAssignment>

    @Modifying
    @Transactional
    @Query("delete from QuestionCategoryAssignment assignment where assignment.questionId = :questionId")
    fun deleteAllByQuestionId(@Param("questionId") questionId: Long)
}
