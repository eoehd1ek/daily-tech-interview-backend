package com.eoehd1ek.tech.question.infrastructure.persistence

import com.eoehd1ek.tech.question.domain.Question
import com.eoehd1ek.tech.question.domain.QuestionCategory
import com.eoehd1ek.tech.question.domain.QuestionCategoryAssignment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest

@DataJpaTest
class QuestionCategoryAssignmentRepositoryTest {
    @Autowired
    private lateinit var questionRepository: QuestionRepository

    @Autowired
    private lateinit var categoryRepository: QuestionCategoryAssignmentRepository

    @Test
    fun `카테고리 연결을 저장하면 생성된 ID와 질문 ID 및 enum 값이 일치한다`() {
        // given
        val questionId = requireNotNull(questionRepository.save(Question("질문", "본문")).id)
        val assignment = QuestionCategoryAssignment(questionId, QuestionCategory.FRONTEND)

        // when
        val result = categoryRepository.save(assignment)

        // then
        assertThat(result.id).isNotNull().isPositive()
        assertThat(result.questionId).isEqualTo(questionId)
        assertThat(result.category).isEqualTo(QuestionCategory.FRONTEND)
    }

    @Test
    fun `지정한 질문들의 연결만 일괄 조회하고 미분류 질문은 빈 연결로 남는다`() {
        // given
        val firstId = requireNotNull(questionRepository.save(Question("첫 질문", "본문")).id)
        val secondId = requireNotNull(questionRepository.save(Question("둘째 질문", "본문")).id)
        val unclassifiedId = requireNotNull(questionRepository.save(Question("미분류 질문", "본문")).id)
        val otherId = requireNotNull(questionRepository.save(Question("제외 질문", "본문")).id)
        val firstFrontend = QuestionCategoryAssignment(firstId, QuestionCategory.FRONTEND)
        val firstBackend = QuestionCategoryAssignment(firstId, QuestionCategory.BACKEND)
        val secondBackend = QuestionCategoryAssignment(secondId, QuestionCategory.BACKEND)
        categoryRepository.saveAll(
            listOf(
                firstBackend, secondBackend, firstFrontend,
                QuestionCategoryAssignment(otherId, QuestionCategory.FRONTEND)
            )
        )

        // when
        val result = categoryRepository.findAllByQuestionIdIn(listOf(firstId, secondId, unclassifiedId))

        // then
        assertThat(result.map { it.id }).containsExactlyInAnyOrder(
            firstFrontend.id, firstBackend.id, secondBackend.id
        )
        assertThat(result.map { it.questionId to it.category }).containsExactlyInAnyOrder(
            firstId to QuestionCategory.FRONTEND,
            firstId to QuestionCategory.BACKEND,
            secondId to QuestionCategory.BACKEND,
        )
    }

    @Test
    fun `질문 ID로 해당 질문의 카테고리 연결만 조회한다`() {
        // given
        val questionId = requireNotNull(questionRepository.save(Question("질문", "본문")).id)
        val otherId = requireNotNull(questionRepository.save(Question("다른 질문", "본문")).id)
        val assignment = categoryRepository.save(QuestionCategoryAssignment(questionId, QuestionCategory.FRONTEND))
        categoryRepository.save(QuestionCategoryAssignment(otherId, QuestionCategory.BACKEND))

        // when
        val result = categoryRepository.findAllByQuestionId(questionId)

        // then
        assertThat(result).hasSize(1)
        assertThat(result.single()).usingRecursiveComparison().isEqualTo(assignment)
    }

    @Test
    fun `카테고리가 없는 질문의 단건 및 일괄 조회는 빈 목록을 반환한다`() {
        // given
        val questionId = requireNotNull(questionRepository.save(Question("미분류 질문", "본문")).id)

        // when
        val assignments = categoryRepository.findAllByQuestionId(questionId)
        val bulkAssignments = categoryRepository.findAllByQuestionIdIn(listOf(questionId))

        // then
        assertThat(assignments).isEmpty()
        assertThat(bulkAssignments).isEmpty()
    }

    @Test
    fun `질문의 카테고리를 전체 삭제하면 다른 질문의 연결은 유지된다`() {
        // given
        val questionId = requireNotNull(questionRepository.save(Question("질문", "본문")).id)
        val otherId = requireNotNull(questionRepository.save(Question("다른 질문", "본문")).id)
        val other = QuestionCategoryAssignment(otherId, QuestionCategory.BACKEND)
        categoryRepository.saveAll(
            listOf(
                QuestionCategoryAssignment(questionId, QuestionCategory.FRONTEND),
                QuestionCategoryAssignment(questionId, QuestionCategory.BACKEND),
                other,
            )
        )

        // when
        categoryRepository.deleteAllByQuestionId(questionId)

        // then
        assertThat(categoryRepository.findAllByQuestionId(questionId)).isEmpty()
        assertThat(categoryRepository.findAllByQuestionId(otherId))
            .usingRecursiveComparison().isEqualTo(listOf(other))
    }

    @Test
    fun `같은 트랜잭션에서 기존 연결을 삭제하고 같은 카테고리를 다시 저장할 수 있다`() {
        // given
        val questionId = requireNotNull(questionRepository.save(Question("질문", "본문")).id)
        val previous = categoryRepository.save(QuestionCategoryAssignment(questionId, QuestionCategory.FRONTEND))

        // when
        categoryRepository.deleteAllByQuestionId(questionId)
        val replacement = categoryRepository.save(QuestionCategoryAssignment(questionId, QuestionCategory.FRONTEND))

        // then
        assertThat(replacement.id).isNotNull().isNotEqualTo(previous.id)
        val result = categoryRepository.findAllByQuestionId(questionId)
        assertThat(result).hasSize(1)
        assertThat(result.single()).usingRecursiveComparison().isEqualTo(replacement)
    }
}
