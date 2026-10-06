package com.eoehd1ek.tech.auth.presentation.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val loginId: String,

    @field:NotBlank
    val password: String,
)
