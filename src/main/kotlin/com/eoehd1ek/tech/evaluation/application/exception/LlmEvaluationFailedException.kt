package com.eoehd1ek.tech.evaluation.application.exception

import com.eoehd1ek.tech.common.presentation.exception.ApplicationException
import com.eoehd1ek.tech.common.presentation.exception.ErrorType

class LlmEvaluationFailedException : ApplicationException(
    type = ErrorType.DEPENDENCY_FAILURE,
    code = "LLM_EVALUATION_FAILED",
    message = "평가 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
)
