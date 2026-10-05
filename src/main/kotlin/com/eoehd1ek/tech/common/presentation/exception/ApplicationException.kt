package com.eoehd1ek.tech.common.presentation.exception

abstract class ApplicationException(
    type: ErrorType,
    code: String,
    message: String,
    cause: Throwable? = null,
) : BusinessException(type, code, message, cause)
