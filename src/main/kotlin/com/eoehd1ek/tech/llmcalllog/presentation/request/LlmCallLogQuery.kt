package com.eoehd1ek.tech.llmcalllog.presentation.request

import com.eoehd1ek.tech.llmcalllog.application.model.LlmCallLogFilter
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallPurpose
import com.eoehd1ek.tech.llmcalllog.domain.LlmCallStatus
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class LlmCallLogQuery(
    val page: String? = null,
    val size: String? = null,
    val status: String? = null,
    val purpose: String? = null,
    val model: String? = null,
    val startedFrom: String? = null,
    val startedTo: String? = null,
) {

    fun toFilter(): LlmCallLogFilter {
        val pageNumber = page?.let { integer(it, 0..2_147_483_646) } ?: 0
        val pageSize = size?.let { integer(it, 1..100) } ?: 20
        val callStatus = status?.let { value ->
            LlmCallStatus.entries.find { it.name == value } ?: invalid()
        }
        val callPurpose = purpose?.let { value ->
            LlmCallPurpose.entries.find { it.name == value } ?: invalid()
        }
        if (model != null && (model.isBlank() || model.length > 255)) invalid()
        val from = startedFrom?.let(::instant)
        val to = startedTo?.let(::instant)
        if (from != null && to != null && !from.isBefore(to)) invalid()
        return LlmCallLogFilter(pageNumber, pageSize, callStatus, callPurpose, model, from, to)
    }

    private fun integer(value: String, range: IntRange): Int {
        if (!value.matches(Regex("[0-9]+"))) invalid()
        val number = value.toIntOrNull() ?: invalid()
        if (number !in range) invalid()
        return number
    }

    private fun instant(value: String): Instant? {
        return try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            invalid()
        }
    }

    private fun invalid(): Nothing {
        throw InvalidLlmCallLogQueryException()
    }
}
