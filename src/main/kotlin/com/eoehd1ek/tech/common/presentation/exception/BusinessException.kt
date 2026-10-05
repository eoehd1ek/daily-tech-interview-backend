package com.eoehd1ek.tech.common.presentation.exception

import org.springframework.http.HttpStatus

abstract class BusinessException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)
