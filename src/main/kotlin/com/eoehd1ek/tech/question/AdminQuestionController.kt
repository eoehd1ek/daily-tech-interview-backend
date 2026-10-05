package com.eoehd1ek.tech.question

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/admin/questions")
class AdminQuestionController(
    private val adminQuestionService: AdminQuestionService,
) {
    @PostMapping
    fun create(@Valid @RequestBody request: AdminQuestionRequest): ResponseEntity<AdminQuestionResponse> {
        val response = adminQuestionService.create(request)
        return ResponseEntity.created(URI.create("/api/admin/questions/${response.id}"))
            .body(response)
    }

    @PutMapping("/{questionId}")
    fun update(
        @PathVariable("questionId") @Min(1) @Max(9_007_199_254_740_991L) questionId: Long,
        @Valid @RequestBody request: AdminQuestionRequest,
    ): AdminQuestionResponse = adminQuestionService.update(questionId, request)
}
