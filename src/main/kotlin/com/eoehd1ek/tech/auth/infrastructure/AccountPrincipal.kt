package com.eoehd1ek.tech.auth.infrastructure

import com.eoehd1ek.tech.account.domain.AccountRole
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User

class AccountPrincipal(
    val id: Long,
    val loginId: String,
    passwordHash: String,
    val role: AccountRole,
) : User(
    loginId,
    passwordHash,
    listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
)
