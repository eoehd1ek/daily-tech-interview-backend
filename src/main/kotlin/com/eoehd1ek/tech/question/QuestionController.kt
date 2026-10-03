package com.eoehd1ek.tech.question

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/questions")
class QuestionController(
    private val questionService: QuestionService,
) {
    @GetMapping
    fun getQuestions(): List<QuestionResponse> = questionService.getQuestions()
}
