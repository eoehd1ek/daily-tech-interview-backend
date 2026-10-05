package com.eoehd1ek.tech.evaluation.domain.exception

import com.eoehd1ek.tech.common.presentation.exception.ApplicationException
import com.eoehd1ek.tech.common.presentation.exception.ErrorType

class EvaluationAttemptNotFoundException : ApplicationException(
    type = ErrorType.NOT_FOUND,
    code = "EVALUATION_ATTEMPT_NOT_FOUND",
    message = "평가 결과를 찾을 수 없습니다.",
)
