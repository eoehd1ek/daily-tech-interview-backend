package com.eoehd1ek.tech.evaluation.infrastructure.persistence

import com.eoehd1ek.tech.config.JpaAuditingConfig
import com.eoehd1ek.tech.evaluation.domain.EvaluationAttempt
import com.eoehd1ek.tech.evaluation.domain.EvaluationResult
import com.eoehd1ek.tech.question.domain.Question
import com.eoehd1ek.tech.question.infrastructure.persistence.QuestionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.Instant

@DataJpaTest
@Import(JpaAuditingConfig::class)
class EvaluationAttemptRepositoryTest {
    @Autowired
    private lateinit var attemptRepository: EvaluationAttemptRepository

    @Autowired
    private lateinit var questionRepository: QuestionRepository

    @Test
    fun `저장한 평가 기록을 ID로 조회하면 저장한 값과 일치한다`() {
        // given
        val question = questionRepository.save(Question("테스트 질문", "테스트 본문"))
        val attempt = EvaluationAttempt(
            questionId = requireNotNull(question.id),
            answer = "테스트 답변",
            score = 82,
            result = EvaluationResult.PASS,
            strengths = "주요 개념을 설명했습니다.",
            weaknesses = "구체적인 사례가 부족합니다.",
            improvements = "사례를 추가해주세요.",
        )
        attemptRepository.save(attempt)

        // when
        val result = attemptRepository.findById(requireNotNull(attempt.id)).orElseThrow()

        // then
        assertThat(result.id).isNotNull().isPositive()
        assertThat(result.createdAt).isNotNull()
        assertThat(result).usingRecursiveComparison().isEqualTo(attempt)
    }

    @Test
    fun `평가 기록의 질문 ID로 평가 대상 질문을 조회한다`() {
        // given
        val question = questionRepository.save(Question("평가 대상 질문", "질문 본문"))
        val attempt = attemptRepository.save(
            EvaluationAttempt(
                questionId = requireNotNull(question.id),
                answer = "답변",
                score = 70,
                result = EvaluationResult.RETRY,
                strengths = "기본 개념을 설명했습니다.",
                weaknesses = "세부 설명이 부족합니다.",
                improvements = "세부 내용을 보완해주세요.",
            ),
        )

        // when
        val savedAttempt = attemptRepository.findById(requireNotNull(attempt.id)).orElseThrow()
        val result = questionRepository.findById(savedAttempt.questionId).orElseThrow()

        // then
        assertThat(savedAttempt.questionId).isEqualTo(question.id)
        assertThat(result).usingRecursiveComparison().isEqualTo(question)
    }

    @Test
    fun `같은 질문의 평가 기록을 여러 개 저장하면 서로 다른 ID로 조회한다`() {
        // given
        val questionId = requireNotNull(questionRepository.save(Question("공통 질문", "본문")).id)
        val first = EvaluationAttempt(
            questionId, "첫 답변", 40, EvaluationResult.FAIL,
            "시도한 부분이 있습니다.", "설명이 부족합니다.", "기본 개념을 복습해주세요.",
        )
        val second = EvaluationAttempt(
            questionId, "두 번째 답변", 90, EvaluationResult.PASS,
            "주요 개념을 설명했습니다.", "일부 예시가 부족합니다.", "예시를 추가해주세요.",
        )
        attemptRepository.saveAll(listOf(first, second))

        // when
        val firstResult = attemptRepository.findById(requireNotNull(first.id)).orElseThrow()
        val secondResult = attemptRepository.findById(requireNotNull(second.id)).orElseThrow()

        // then
        assertThat(firstResult.id).isNotEqualTo(secondResult.id)
        assertThat(firstResult.questionId).isEqualTo(secondResult.questionId)
        assertThat(firstResult).usingRecursiveComparison().isEqualTo(first)
        assertThat(secondResult).usingRecursiveComparison().isEqualTo(second)
    }

    @Test
    fun `평가 기록을 저장하면 JPA Auditing이 생성 시각을 설정한다`() {
        // given
        val questionId = requireNotNull(questionRepository.save(Question("테스트 질문", "본문")).id)
        val attempt = EvaluationAttempt(
            questionId, "답변", 80, EvaluationResult.PASS,
            "잘 설명했습니다.", "보완할 점이 있습니다.", "예시를 추가해주세요.",
        )
        val beforeSave = Instant.now()
        val createdAtBeforeSave = attempt.createdAt

        // when
        val savedAttempt = attemptRepository.save(attempt)
        val afterSave = Instant.now()
        val result = attemptRepository.findById(requireNotNull(savedAttempt.id)).orElseThrow()

        // then
        assertThat(createdAtBeforeSave).isNull()
        assertThat(result.createdAt).isNotNull()
        assertThat(requireNotNull(result.createdAt)).isBetween(beforeSave, afterSave)
        assertThat(result.createdAt).isEqualTo(savedAttempt.createdAt)
    }

    @Test
    fun `평가 기록의 답변은 앞뒤 공백과 줄바꿈을 그대로 유지한다`() {
        // given
        val questionId = requireNotNull(questionRepository.save(Question("테스트 질문", "본문")).id)
        val answer = "  첫 번째 줄\n두 번째 줄  "
        val attempt = EvaluationAttempt(
            questionId, answer, 60, EvaluationResult.RETRY,
            "개념을 설명했습니다.", "세부 내용이 부족합니다.", "내용을 보완해주세요.",
        )
        attemptRepository.save(attempt)

        // when
        val result = attemptRepository.findById(requireNotNull(attempt.id)).orElseThrow()

        // then
        assertThat(result.answer).isEqualTo(answer)
    }
}
