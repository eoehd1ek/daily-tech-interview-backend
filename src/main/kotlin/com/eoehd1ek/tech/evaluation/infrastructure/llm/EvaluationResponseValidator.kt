package com.eoehd1ek.tech.evaluation.infrastructure.llm

import com.eoehd1ek.tech.evaluation.application.EvaluationCriterionSpec
import com.eoehd1ek.tech.evaluation.application.result.EvaluatedAnswerResult
import com.eoehd1ek.tech.evaluation.domain.EvaluationResult
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Component
class EvaluationResponseValidator {
    private val mapper = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build()

    fun validate(content: String, criteria: List<EvaluationCriterionSpec>): EvaluatedAnswerResult {
        val root = try {
            mapper.readTree(content)
        } catch (_: JacksonException) {
            throw InvalidLlmResponseException()
        }
        if (root == null || !root.isObject) throw InvalidLlmResponseException()
        val items = root.get("criteria")
        if (items == null || !items.isArray || items.size() != criteria.size) {
            throw InvalidLlmResponseException()
        }

        val expected = criteria.associateBy { it.id }
        val seen = mutableSetOf<Long>()
        var total = 0
        for (item in items) {
            if (!item.isObject) throw InvalidLlmResponseException()
            val id = item.get("criterionId")
            if (id == null || !id.isIntegralNumber || !id.canConvertToLong()) {
                throw InvalidLlmResponseException()
            }
            val criterionId = id.asLong()
            val criterion = expected[criterionId]
                ?: throw InvalidLlmResponseException()
            if (!seen.add(criterionId)) throw InvalidLlmResponseException()
            val score = item.get("score")
            if (score == null || !score.isIntegralNumber || !score.canConvertToInt()) {
                throw InvalidLlmResponseException()
            }
            val value = score.asInt()
            if (value !in 0..criterion.maxScore) throw InvalidLlmResponseException()
            requiredText(item, "feedback")
            total += value
        }

        return EvaluatedAnswerResult(
            score = total,
            result = when {
                total < 50 -> EvaluationResult.FAIL
                total < 80 -> EvaluationResult.RETRY
                else -> EvaluationResult.PASS
            },
            strengths = requiredText(root, "strengths"),
            weaknesses = requiredText(root, "weaknesses"),
            improvements = requiredText(root, "improvements"),
        )
    }

    private fun requiredText(node: JsonNode, field: String): String {
        val value = node.get(field)
        if (value == null || !value.isString || value.asString().isBlank()) {
            throw InvalidLlmResponseException()
        }
        return value.asString()
    }
}
