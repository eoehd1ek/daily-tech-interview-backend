package com.eoehd1ek.tech.evaluation.presentation

import com.eoehd1ek.tech.evaluation.application.EvaluationService
import com.eoehd1ek.tech.evaluation.presentation.request.SubmitAnswerRequest
import com.eoehd1ek.tech.evaluation.presentation.response.EvaluationAttemptResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
class EvaluationAttemptController(
    private val evaluationService: EvaluationService,
) {
    @PostMapping("/api/questions/{questionId}/evaluation-attempts")
    fun submitAnswer(
        @PathVariable("questionId")
        @Min(1)
        @Max(9_007_199_254_740_991L)
        questionId: Long,

        @Valid
        @RequestBody
        request: SubmitAnswerRequest,
    ): ResponseEntity<EvaluationAttemptResponse> {
        val result = evaluationService.submitAnswer(questionId, request.answer)
        val response = EvaluationAttemptResponse.from(result)
        return ResponseEntity
            .created(URI.create("/api/evaluation-attempts/${response.id}"))
            .body(response)
    }

    @GetMapping("/api/evaluation-attempts/{attemptId}")
    fun getAttempt(
        @PathVariable("attemptId")
        @Min(1)
        @Max(9_007_199_254_740_991L)
        attemptId: Long,
    ): EvaluationAttemptResponse {
        val attempt = evaluationService.getAttempt(attemptId)
        return EvaluationAttemptResponse.from(attempt)
    }
}
