package com.eoehd1ek.tech.question.presentation

import com.eoehd1ek.tech.question.application.QuestionService
import com.eoehd1ek.tech.question.presentation.response.QuestionDetailResponse
import com.eoehd1ek.tech.question.presentation.response.QuestionResponse
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/questions")
class QuestionController(
    private val questionService: QuestionService,
) {
    @GetMapping
    fun getQuestions(): List<QuestionResponse> = questionService.getQuestions()

    @GetMapping("/{questionId}")
    fun getQuestion(
        @PathVariable("questionId") @Min(1) @Max(9_007_199_254_740_991L) questionId: Long,
    ): QuestionDetailResponse = questionService.getQuestion(questionId)
}
