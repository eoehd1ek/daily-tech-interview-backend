package com.eoehd1ek.tech.evaluation

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class EvaluationPreviewController(
    private val evaluationService: EvaluationService,
) {
    @PostMapping("/api/admin/questions/evaluation-preview")
    fun preview(@Valid @RequestBody request: EvaluationPreviewRequest): EvaluationPreviewResponse =
        evaluationService.preview(request)
}
