package com.eoehd1ek.tech.question.presentation.request

import com.eoehd1ek.tech.question.domain.QuestionCategory
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

class QuestionCategoriesDeserializer : ValueDeserializer<List<QuestionCategory>>() {

    override fun deserialize(parser: JsonParser, context: DeserializationContext): List<QuestionCategory> {
        if (parser.currentToken() != JsonToken.START_ARRAY) return invalid(context)

        val categories = mutableListOf<QuestionCategory>()
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.VALUE_STRING || categories.size == 5) return invalid(context)
            val category = QuestionCategory.entries.find { it.name == parser.string } ?: return invalid(context)
            if (category in categories) return invalid(context)
            categories += category
        }
        return categories
    }

    private fun invalid(context: DeserializationContext): List<QuestionCategory> {
        return context.reportInputMismatch(
            List::class.java,
            "categories must contain up to five distinct supported strings"
        )
    }
}
