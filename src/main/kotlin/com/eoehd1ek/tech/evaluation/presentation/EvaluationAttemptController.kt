package com.eoehd1ek.tech.evaluation.presentation

import com.eoehd1ek.tech.evaluation.presentation.request.EvaluationAttemptRequest
import com.eoehd1ek.tech.evaluation.presentation.response.EvaluationAttemptResponse
import com.eoehd1ek.tech.evaluation.application.EvaluationService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
class EvaluationAttemptController(
    private val evaluationService: EvaluationService,
) {
    @PostMapping("/api/questions/{questionId}/evaluation-attempts")
    fun submit(
        @PathVariable("questionId") @Min(1) @Max(9_007_199_254_740_991L) questionId: Long,
        @Valid @RequestBody request: EvaluationAttemptRequest,
    ): ResponseEntity<EvaluationAttemptResponse> {
        val response = evaluationService.submit(questionId, request.answer)
        return ResponseEntity.created(URI.create("/api/evaluation-attempts/${response.id}"))
            .body(response)
    }

    @GetMapping("/api/evaluation-attempts/{attemptId}")
    fun getAttempt(
        @PathVariable("attemptId") @Min(1) @Max(9_007_199_254_740_991L) attemptId: Long,
    ): EvaluationAttemptResponse = evaluationService.getAttempt(attemptId)
}
