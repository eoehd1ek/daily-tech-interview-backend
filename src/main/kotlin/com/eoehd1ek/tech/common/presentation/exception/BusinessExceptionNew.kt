package com.eoehd1ek.tech.common.presentation.exception

abstract class BusinessExceptionNew(
    val type: ErrorType,
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
