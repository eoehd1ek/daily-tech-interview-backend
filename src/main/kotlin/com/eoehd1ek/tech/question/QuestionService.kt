package com.eoehd1ek.tech.question

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QuestionService(
    private val questionRepository: QuestionRepository,
) {
    @Transactional(readOnly = true)
    fun getQuestions(): List<QuestionResponse> =
        questionRepository.findAll().map { question ->
            QuestionResponse(
                id = checkNotNull(question.id),
                title = question.title,
            )
        }
}
