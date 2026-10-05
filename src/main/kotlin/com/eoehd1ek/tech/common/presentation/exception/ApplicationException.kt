package com.eoehd1ek.tech.common.presentation.exception

abstract class ApplicationException(
    type: ErrorType,
    code: String,
    message: String,
    cause: Throwable? = null,
) : BusinessExceptionNew(type, code, message, cause)
