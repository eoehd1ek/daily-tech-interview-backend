package com.eoehd1ek.tech.question.presentation

import com.eoehd1ek.tech.question.application.AdminQuestionService
import com.eoehd1ek.tech.question.application.QuestionService
import com.eoehd1ek.tech.question.presentation.request.AdminQuestionRequest
import com.eoehd1ek.tech.question.presentation.response.AdminQuestionResponse
import com.eoehd1ek.tech.question.presentation.response.QuestionResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/api/admin/questions")
class AdminQuestionController(
    private val adminQuestionService: AdminQuestionService,
    private val questionService: QuestionService,
) {
    @GetMapping
    fun getQuestions(): List<QuestionResponse> =
        questionService.getQuestions()

    @GetMapping("/{questionId}")
    fun getQuestion(
        @PathVariable("questionId")
        @Min(1)
        @Max(9_007_199_254_740_991L)
        questionId: Long,
    ): AdminQuestionResponse {
        val result = adminQuestionService.getQuestion(questionId)
        return AdminQuestionResponse
            .from(
                result.question,
                result.criteria
            )
    }

    @PostMapping
    fun create(
        @Valid
        @RequestBody
        request: AdminQuestionRequest
    ): ResponseEntity<AdminQuestionResponse> {
        val response = adminQuestionService.create(request)
        return ResponseEntity
            .created(URI.create("/api/admin/questions/${response.id}"))
            .body(response)
    }

    @PutMapping("/{questionId}")
    fun update(
        @PathVariable("questionId")
        @Min(1)
        @Max(9_007_199_254_740_991L)
        questionId: Long,

        @Valid
        @RequestBody
        request: AdminQuestionRequest,
    ): AdminQuestionResponse =
        adminQuestionService.update(questionId, request)
}
