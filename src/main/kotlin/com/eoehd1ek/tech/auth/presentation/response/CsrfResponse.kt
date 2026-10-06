package com.eoehd1ek.tech.auth.presentation.response

data class CsrfResponse(
    val headerName: String,
    val parameterName: String,
    val token: String
)
