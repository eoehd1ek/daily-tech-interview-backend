package com.eoehd1ek.tech.evaluation.infrastructure.llm

import com.eoehd1ek.tech.evaluation.application.model.EvaluationProviderResult
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Component
class LlmEvaluationResponseParser {
    private val mapper = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build()

    fun parse(content: String): EvaluationProviderResult {
        val root = parseRoot(content)

        return EvaluationProviderResult(
            criteria = root.requiredArray("criteria").map(::parseCriterion),
            strengths = root.requiredNonBlankString("strengths"),
            weaknesses = root.requiredNonBlankString("weaknesses"),
            improvements = root.requiredNonBlankString("improvements"),
        )
    }

    private fun parseRoot(content: String): JsonNode {
        val root = try {
            mapper.readTree(content)
        } catch (exception: JacksonException) {
            throw InvalidLlmResponseException(exception)
        }

        if (root == null || !root.isObject) {
            throw InvalidLlmResponseException()
        }

        return root
    }

    private fun parseCriterion(node: JsonNode): EvaluationProviderResult.CriterionResult {
        if (!node.isObject) {
            throw InvalidLlmResponseException()
        }

        return EvaluationProviderResult.CriterionResult(
            criterionId = node.requiredLong("criterionId"),
            score = node.requiredInt("score"),
            feedback = node.requiredNonBlankString("feedback"),
        )
    }

    private fun JsonNode.requiredArray(field: String): List<JsonNode> {
        val value = get(field)

        if (value == null || !value.isArray) {
            throw InvalidLlmResponseException()
        }

        return value.toList()
    }

    private fun JsonNode.requiredLong(field: String): Long {
        val value = get(field)

        if (value == null || !value.isIntegralNumber || !value.canConvertToLong()) {
            throw InvalidLlmResponseException()
        }

        return value.asLong()
    }

    private fun JsonNode.requiredInt(field: String): Int {
        val value = get(field)

        if (value == null || !value.isIntegralNumber || !value.canConvertToInt()) {
            throw InvalidLlmResponseException()
        }

        return value.asInt()
    }

    private fun JsonNode.requiredNonBlankString(field: String): String {
        val value = get(field)

        if (value == null || !value.isString || value.asString().isBlank()) {
            throw InvalidLlmResponseException()
        }

        return value.asString()
    }
}
