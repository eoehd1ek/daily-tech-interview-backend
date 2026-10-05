package com.eoehd1ek.tech.evaluation

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize

data class EvaluationAttemptRequest(
    @field:NotBlank
    @field:Size(max = 3000)
    @field:JsonDeserialize(using = AnswerDeserializer::class)
    val answer: String,
)

class AnswerDeserializer : ValueDeserializer<String>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): String =
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            parser.string
        } else {
            context.reportInputMismatch(String::class.java, "answer must be a JSON string")
        }
}
