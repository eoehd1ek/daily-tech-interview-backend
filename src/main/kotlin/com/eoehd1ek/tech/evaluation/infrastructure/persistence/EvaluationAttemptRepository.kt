package com.eoehd1ek.tech.evaluation.infrastructure.persistence

import com.eoehd1ek.tech.evaluation.domain.EvaluationAttempt
import org.springframework.data.jpa.repository.JpaRepository

interface EvaluationAttemptRepository : JpaRepository<EvaluationAttempt, Long>
