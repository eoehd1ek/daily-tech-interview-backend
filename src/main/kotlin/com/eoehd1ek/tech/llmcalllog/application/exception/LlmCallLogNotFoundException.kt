package com.eoehd1ek.tech.llmcalllog.application.exception

import com.eoehd1ek.tech.common.presentation.exception.ApplicationException
import com.eoehd1ek.tech.common.presentation.exception.ErrorType

class LlmCallLogNotFoundException : ApplicationException(
    ErrorType.NOT_FOUND,
    "LLM_CALL_LOG_NOT_FOUND",
    "LLM 호출 기록을 찾을 수 없습니다.",
)
