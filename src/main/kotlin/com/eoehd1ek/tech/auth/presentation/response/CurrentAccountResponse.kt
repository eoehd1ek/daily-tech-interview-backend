package com.eoehd1ek.tech.auth.presentation.response

import com.eoehd1ek.tech.account.domain.AccountRole
import com.eoehd1ek.tech.auth.infrastructure.AccountPrincipal

data class CurrentAccountResponse(
    val id: Long,
    val loginId: String,
    val role: AccountRole
) {

    companion object {
        fun from(principal: AccountPrincipal) =
            CurrentAccountResponse(
                principal.id,
                principal.loginId,
                principal.role
            )
    }
}
