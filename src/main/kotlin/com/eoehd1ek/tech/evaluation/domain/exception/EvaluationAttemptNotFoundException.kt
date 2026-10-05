package com.eoehd1ek.tech.evaluation.domain.exception

import com.eoehd1ek.tech.common.presentation.exception.BusinessException
import org.springframework.http.HttpStatus

class EvaluationAttemptNotFoundException : BusinessException(
    status = HttpStatus.NOT_FOUND,
    code = "EVALUATION_ATTEMPT_NOT_FOUND",
    message = "평가 결과를 찾을 수 없습니다.",
)
