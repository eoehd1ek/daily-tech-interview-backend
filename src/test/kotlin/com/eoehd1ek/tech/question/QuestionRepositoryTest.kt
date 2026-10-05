package com.eoehd1ek.tech.question

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest

@DataJpaTest
class QuestionRepositoryTest {
    @Autowired
    private lateinit var questionRepository: QuestionRepository

    @Autowired
    private lateinit var criterionRepository: EvaluationCriterionRepository

    @Test
    fun `저장한 질문을 ID로 조회하면 저장한 값과 일치한다`() {
        // given
        val question = Question("테스트 질문", "테스트 질문 본문")
        questionRepository.save(question)
        val questionId = requireNotNull(question.id)

        // when
        val result = questionRepository.findById(questionId).orElseThrow()

        // then
        assertThat(result.id).isNotNull().isPositive()
        assertThat(result).usingRecursiveComparison().isEqualTo(question)
    }

    @Test
    fun `저장한 평가 기준을 질문 ID로 조회하면 저장한 값과 일치한다`() {
        // given
        val question = questionRepository.save(Question("테스트 질문", "테스트 본문"))
        val questionId = requireNotNull(question.id)
        val criterion = EvaluationCriterion(questionId, "테스트 평가 기준", 100, 1)
        criterionRepository.save(criterion)

        // when
        val result = criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)

        // then
        assertThat(result).hasSize(1)
        assertThat(result.single().id).isNotNull().isPositive()
        assertThat(result.single()).usingRecursiveComparison().isEqualTo(criterion)
    }

    @Test
    fun `해당 질문의 평가 기준만 표시 순서와 ID 오름차순으로 조회한다`() {
        // given
        val question = Question("첫 질문", "본문")
        val questionId = requireNotNull(questionRepository.save(question).id)
        val otherQuestionId = requireNotNull(questionRepository.save(Question("다른 질문", "본문")).id)
        val last = EvaluationCriterion(questionId, "마지막 기준", 20, 3)
        val first = EvaluationCriterion(questionId, "첫 기준", 40, 1)
        val tied = EvaluationCriterion(questionId, "같은 순서의 기준", 40, 1)
        val other = EvaluationCriterion(otherQuestionId, "다른 질문의 기준", 100, 1)
        criterionRepository.saveAll(listOf(last, first, tied, other))

        // when
        val result = criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(listOf(first, tied, last))
        assertThat(result).allSatisfy { assertThat(it.questionId).isEqualTo(questionId) }
    }

    @Test
    fun `평가 기준이 없는 질문을 조회하면 빈 목록을 반환한다`() {
        // given
        val question = Question("기준 없는 질문", "본문")
        val questionId = requireNotNull(questionRepository.save(question).id)

        // when
        val result = criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun `해당 질문의 기준을 삭제하면 다른 질문의 기준은 유지된다`() {
        // given
        val questionId = requireNotNull(questionRepository.save(Question("질문", "본문")).id)
        val otherId = requireNotNull(questionRepository.save(Question("다른 질문", "본문")).id)
        criterionRepository.save(EvaluationCriterion(questionId, "삭제할 기준", 100, 1))
        val other = criterionRepository.save(EvaluationCriterion(otherId, "유지할 기준", 100, 1))

        // when
        criterionRepository.deleteAllByQuestionId(questionId)

        // then
        assertThat(criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)).isEmpty()
        assertThat(criterionRepository.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(otherId))
            .usingRecursiveComparison().isEqualTo(listOf(other))
    }

    @Test
    fun `질문의 제목과 본문을 변경하면 ID는 유지된다`() {
        // given
        val question = questionRepository.save(Question("이전 제목", "이전 본문"))
        val questionId = requireNotNull(question.id)

        // when
        question.changeTitle("변경된 제목")
        question.changeContent("변경된 본문")
        questionRepository.save(question)

        // then
        val result = questionRepository.findById(questionId).orElseThrow()
        assertThat(result.id).isEqualTo(questionId)
        assertThat(result.title).isEqualTo("변경된 제목")
        assertThat(result.content).isEqualTo("변경된 본문")
    }
}
