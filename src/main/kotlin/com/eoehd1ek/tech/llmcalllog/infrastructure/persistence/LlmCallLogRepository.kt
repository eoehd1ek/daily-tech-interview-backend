package com.eoehd1ek.tech.llmcalllog.infrastructure.persistence

import com.eoehd1ek.tech.llmcalllog.domain.LlmCallLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface LlmCallLogRepository : JpaRepository<LlmCallLog, Long> {

    // Native OFFSET supports the API's full page range beyond JPA's Int offset limit.
    @Query(
        value = """
        SELECT id, purpose, status, provider, requested_model AS "requestedModel",
               response_model AS "responseModel", started_at AS "startedAt",
               completed_at AS "completedAt", duration_ms AS "durationMs",
               input_tokens AS "inputTokens", output_tokens AS "outputTokens",
               total_tokens AS "totalTokens", failure_stage AS "failureStage"
        FROM llm_call_log
        WHERE (CAST(:status AS varchar) IS NULL OR status = :status)
          AND (CAST(:purpose AS varchar) IS NULL OR purpose = :purpose)
          AND (CAST(:model AS varchar) IS NULL OR requested_model = :model)
          AND (CAST(:startedFrom AS timestamptz) IS NULL OR started_at >= :startedFrom)
          AND (CAST(:startedTo AS timestamptz) IS NULL OR started_at < :startedTo)
        ORDER BY started_at DESC, id DESC
        LIMIT :limit OFFSET :offset
    """, nativeQuery = true
    )
    fun findSummaries(
        @Param("status") status: String?,
        @Param("purpose") purpose: String?,
        @Param("model") model: String?,
        @Param("startedFrom") startedFrom: Instant?,
        @Param("startedTo") startedTo: Instant?,
        @Param("limit") limit: Int,
        @Param("offset") offset: Long,
    ): List<LlmCallLogSummaryProjection>

    @Query(
        value = """
        SELECT COUNT(*) FROM llm_call_log
        WHERE (CAST(:status AS varchar) IS NULL OR status = :status)
          AND (CAST(:purpose AS varchar) IS NULL OR purpose = :purpose)
          AND (CAST(:model AS varchar) IS NULL OR requested_model = :model)
          AND (CAST(:startedFrom AS timestamptz) IS NULL OR started_at >= :startedFrom)
          AND (CAST(:startedTo AS timestamptz) IS NULL OR started_at < :startedTo)
    """, nativeQuery = true
    )
    fun countMatching(
        @Param("status") status: String?,
        @Param("purpose") purpose: String?,
        @Param("model") model: String?,
        @Param("startedFrom") startedFrom: Instant?,
        @Param("startedTo") startedTo: Instant?,
    ): Long
}
