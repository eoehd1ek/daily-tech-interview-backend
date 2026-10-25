package com.eoehd1ek.tech.llmcalllog.presentation.request

import com.eoehd1ek.tech.common.presentation.exception.ApplicationException
import com.eoehd1ek.tech.common.presentation.exception.ErrorType

class InvalidLlmCallLogQueryException : ApplicationException(
    ErrorType.INVALID_INPUT,
    "INVALID_REQUEST",
    "요청 값의 형식이나 범위가 올바르지 않습니다.",
)
