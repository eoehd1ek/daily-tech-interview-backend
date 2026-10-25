package com.eoehd1ek.tech.evaluation.infrastructure.llm

import com.eoehd1ek.tech.llmcalllog.application.model.CompleteLlmCallLog
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallFailureStage
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallStatus
import com.openai.core.JsonValue
import com.openai.models.completions.CompletionUsage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.time.Instant
import java.util.*

internal object LlmCallSnapshot {
    private val mapper = JsonMapper.builder().build()

    fun provider(options: OpenAiChatOptions): String {
        return options.baseUrl?.let {
            runCatching { URI(it).host?.lowercase(Locale.ROOT) }.getOrNull() ?: "unknown"
        } ?: "api.openai.com"
    }

    fun requestMessages(prompt: Prompt): String {
        return mapper.writeValueAsString(prompt.instructions.map {
            mapOf(
                "role" to it.messageType.name.lowercase(Locale.ROOT),
                "content" to it.text
            )
        })
    }

    fun requestOptions(options: OpenAiChatOptions): String {
        return mapper.writeValueAsString(
            mapOf(
                "model" to options.model,
                "maxTokens" to options.maxTokens,
                "maxCompletionTokens" to options.maxCompletionTokens,
                "reasoningEffort" to options.reasoningEffort,
                "temperature" to options.temperature,
                "topP" to options.topP,
                "frequencyPenalty" to options.frequencyPenalty,
                "presencePenalty" to options.presencePenalty,
                "seed" to options.seed,
                "stop" to options.stop,
                "n" to options.n,
                "verbosity" to options.verbosity,
                "serviceTier" to options.serviceTier,
                "responseFormat" to options.responseFormat?.let {
                    mapOf(
                        "type" to it.type.name.lowercase(Locale.ROOT),
                        "strict" to it.strict
                    ).filterValues { value -> value != null }
                },
            ).filterValues { it != null })
    }

    fun completion(
        response: ChatResponse?,
        durationMs: Long,
        failureStage: LlmCallFailureStage?,
        failure: RuntimeException?,
    ): CompleteLlmCallLog {
        val usage = response?.metadata?.usage?.nativeUsage as? CompletionUsage
        val promptDetails = usage?._promptTokensDetails()?.asKnown()?.orElse(null)
        val completionDetails = usage?._completionTokensDetails()?.asKnown()?.orElse(null)
        return CompleteLlmCallLog(
            status = if (failureStage == null) LlmCallStatus.SUCCEEDED else LlmCallStatus.FAILED,
            completedAt = Instant.now(),
            durationMs = durationMs,
            responseId = response?.metadata?.id?.takeIf { it.isNotBlank() },
            responseModel = response?.metadata?.model?.takeIf { it.isNotBlank() },
            finishReason = response?.result?.metadata?.finishReason?.takeIf { it.isNotBlank() },
            responseData = response?.let {
                mapper.writeValueAsString(
                    mapOf(
                        "results" to it.results.mapIndexed { index, generation ->
                            mapOf(
                                "index" to index,
                                "text" to generation.output.text,
                                "finishReason" to generation.metadata.finishReason?.takeIf { reason -> reason.isNotBlank() },
                                "metadata" to mapOf(
                                    "generation" to safeJson(
                                        generation.metadata.entrySet().associate { entry -> entry.key to entry.value }),
                                    "output" to safeJson(generation.output.metadata),
                                ),
                            )
                        },
                        "metadata" to safeJson(it.metadata.entrySet().associate { entry -> entry.key to entry.value }),
                        "usage" to usage?.let(::usageSnapshot),
                    )
                )
            },
            inputTokens = usage?._promptTokens()?.asKnown()?.orElse(null),
            outputTokens = usage?._completionTokens()?.asKnown()?.orElse(null),
            totalTokens = usage?._totalTokens()?.asKnown()?.orElse(null),
            cachedInputTokens = promptDetails?._cachedTokens()?.asKnown()?.orElse(null),
            reasoningTokens = completionDetails?._reasoningTokens()?.asKnown()?.orElse(null),
            failureStage = failureStage,
            errorData = failureStage?.let { errorSnapshot(it, failure) },
        )
    }

    private fun usageSnapshot(usage: CompletionUsage): Map<String, Any?> {
        val promptDetails = usage._promptTokensDetails().asKnown().orElse(null)
        val completionDetails = usage._completionTokensDetails().asKnown().orElse(null)
        return mapOf(
            "additionalProperties" to safeJson(usage._additionalProperties()),
            "promptTokens" to usage._promptTokens().asKnown().orElse(null),
            "completionTokens" to usage._completionTokens().asKnown().orElse(null),
            "totalTokens" to usage._totalTokens().asKnown().orElse(null),
            "promptTokensDetails" to promptDetails?.let {
                mapOf(
                    "audioTokens" to it._audioTokens().asKnown().orElse(null),
                    "cacheWriteTokens" to it._cacheWriteTokens().asKnown().orElse(null),
                    "cachedTokens" to it._cachedTokens().asKnown().orElse(null),
                    "additionalProperties" to safeJson(it._additionalProperties()),
                )
            },
            "completionTokensDetails" to completionDetails?.let {
                mapOf(
                    "acceptedPredictionTokens" to it._acceptedPredictionTokens().asKnown().orElse(null),
                    "audioTokens" to it._audioTokens().asKnown().orElse(null),
                    "reasoningTokens" to it._reasoningTokens().asKnown().orElse(null),
                    "rejectedPredictionTokens" to it._rejectedPredictionTokens().asKnown().orElse(null),
                    "additionalProperties" to safeJson(it._additionalProperties()),
                )
            },
        )
    }

    private fun errorSnapshot(stage: LlmCallFailureStage, failure: RuntimeException?): String {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        failure?.let(seen::add)
        val causes = mutableListOf<Map<String, String?>>()
        var cause = failure?.cause
        while (cause != null && seen.add(cause)) {
            causes += mapOf("type" to cause.javaClass.simpleName, "message" to null)
            cause = cause.cause
        }
        return mapper.writeValueAsString(
            mapOf(
                "type" to (failure?.javaClass?.simpleName ?: "EmptyLlmResponse"),
                "message" to when (stage) {
                    LlmCallFailureStage.MODEL_CALL -> "Model call failed"
                    LlmCallFailureStage.EMPTY_RESPONSE -> "Model response text is empty"
                    LlmCallFailureStage.RESPONSE_PARSE -> "Model response parsing failed"
                },
                "causes" to causes,
            )
        )
    }

    // Never ask Jackson to inspect arbitrary SDK objects or invoke their toString().
    private fun safeJson(value: Any?, depth: Int = 0): Any? {
        if (depth > 32) return null
        return when (value) {
            null, is String, is Boolean, is Byte, is Short, is Int, is Long, is BigInteger, is BigDecimal -> value
            is Float -> value.takeIf { it.isFinite() }
            is Double -> value.takeIf { it.isFinite() }
            is Map<*, *> -> value.entries.filter { it.key is String && safeKey(it.key as String) }
                .associate { it.key as String to safeJson(it.value, depth + 1) }

            is List<*> -> value.map { safeJson(it, depth + 1) }
            is JsonValue -> when {
                value.isNull() || value.isMissing() -> null
                value.asBoolean().isPresent -> value.asBoolean().get()
                value.asNumber().isPresent -> safeJson(value.asNumber().get(), depth + 1)
                value.asString().isPresent -> value.asString().get()
                value.asArray().isPresent -> safeJson(value.asArray().get(), depth + 1)
                value.asObject().isPresent -> safeJson(value.asObject().get(), depth + 1)
                else -> null
            }

            else -> null
        }
    }

    private fun safeKey(key: String): Boolean {
        val normalized = key.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        return listOf(
            "authorization", "apikey", "credential", "cookie", "csrf", "password", "secret",
            "header", "schema", "baseurl", "session", "accesstoken", "refreshtoken", "privatekey"
        ).none { it in normalized }
    }
}
