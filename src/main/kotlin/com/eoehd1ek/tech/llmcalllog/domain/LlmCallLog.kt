package com.eoehd1ek.tech.llmcalllog.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@Table(name = "llm_call_log")
@EntityListeners(AuditingEntityListener::class)
class LlmCallLog(
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    val purpose: LlmCallPurpose,

    @field:Column(name = "question_id")
    val questionId: Long?,

    @field:Column(nullable = false)
    val provider: String,

    @field:Column(name = "requested_model", nullable = false)
    val requestedModel: String,

    @field:Column(name = "started_at", nullable = false)
    val startedAt: Instant,

    @field:Column(name = "request_messages", nullable = false)
    val requestMessages: String,

    @field:Column(name = "request_options", nullable = false)
    val requestOptions: String,
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @field:CreatedDate
    @field:Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var status: LlmCallStatus = LlmCallStatus.STARTED
        internal set

    @field:Column(name = "evaluation_attempt_id")
    var evaluationAttemptId: Long? = null
        internal set

    @field:Column(name = "completed_at")
    var completedAt: Instant? = null
        internal set

    @field:Column(name = "duration_ms")
    var durationMs: Long? = null
        internal set

    @field:Column(name = "response_id")
    var responseId: String? = null
        internal set

    @field:Column(name = "response_model")
    var responseModel: String? = null
        internal set

    @field:Column(name = "finish_reason")
    var finishReason: String? = null
        internal set

    @field:Column(name = "response_data")
    var responseData: String? = null
        internal set

    @field:Column(name = "input_tokens")
    var inputTokens: Long? = null
        internal set

    @field:Column(name = "output_tokens")
    var outputTokens: Long? = null
        internal set

    @field:Column(name = "total_tokens")
    var totalTokens: Long? = null
        internal set

    @field:Column(name = "cached_input_tokens")
    var cachedInputTokens: Long? = null
        internal set

    @field:Column(name = "reasoning_tokens")
    var reasoningTokens: Long? = null
        internal set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "failure_stage")
    var failureStage: LlmCallFailureStage? = null
        internal set

    @field:Column(name = "error_data")
    var errorData: String? = null
        internal set
}
