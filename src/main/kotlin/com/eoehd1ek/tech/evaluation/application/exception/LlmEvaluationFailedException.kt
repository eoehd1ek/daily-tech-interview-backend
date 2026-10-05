package com.eoehd1ek.tech.evaluation.application.exception

import com.eoehd1ek.tech.common.presentation.exception.BusinessException
import org.springframework.http.HttpStatus

class LlmEvaluationFailedException : BusinessException(
    status = HttpStatus.BAD_GATEWAY,
    code = "LLM_EVALUATION_FAILED",
    message = "평가 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
)
