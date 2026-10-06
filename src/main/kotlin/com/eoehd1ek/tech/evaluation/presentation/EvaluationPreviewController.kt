package com.eoehd1ek.tech.evaluation.presentation

import com.eoehd1ek.tech.evaluation.application.EvaluationService
import com.eoehd1ek.tech.evaluation.application.model.EvaluationCriterionSpec
import com.eoehd1ek.tech.evaluation.presentation.request.EvaluationPreviewRequest
import com.eoehd1ek.tech.evaluation.presentation.response.EvaluationPreviewResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class EvaluationPreviewController(
    private val evaluationService: EvaluationService,
) {
    @PostMapping("/api/admin/questions/evaluation-preview")
    fun previewEvaluation(
        @Valid
        @RequestBody
        request: EvaluationPreviewRequest
    ): EvaluationPreviewResponse {
        val criteria = request.criteria
            .map { requireNotNull(it) }
            .sortedBy { it.displayOrder }
            .mapIndexed { index, criterion ->
                EvaluationCriterionSpec(
                    id = index + 1L,
                    content = criterion.content,
                    maxScore = criterion.maxScore,
                )
            }
        val result = evaluationService.previewEvaluation(request.title, request.content, criteria, request.answer)
        return EvaluationPreviewResponse(
            questionTitle = request.title,
            answer = request.answer,
            score = result.score,
            result = result.result,
            strengths = result.strengths,
            weaknesses = result.weaknesses,
            improvements = result.improvements,
        )
    }
}
