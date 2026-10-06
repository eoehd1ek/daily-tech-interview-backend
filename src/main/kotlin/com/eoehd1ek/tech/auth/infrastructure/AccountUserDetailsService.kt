package com.eoehd1ek.tech.auth.infrastructure

import com.eoehd1ek.tech.account.infrastructure.persistence.UserAccountRepository
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountUserDetailsService(
    private val repository: UserAccountRepository
) : UserDetailsService {

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): AccountPrincipal {
        val account = repository.findByLoginId(username)
            ?: throw UsernameNotFoundException("계정을 찾을 수 없습니다.")

        return AccountPrincipal(
            id = checkNotNull(account.id),
            loginId = account.loginId,
            passwordHash = account.passwordHash,
            role = account.role
        )
    }
}
