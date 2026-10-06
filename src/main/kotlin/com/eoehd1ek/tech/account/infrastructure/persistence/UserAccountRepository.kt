package com.eoehd1ek.tech.account.infrastructure.persistence

import com.eoehd1ek.tech.account.domain.UserAccount
import org.springframework.data.jpa.repository.JpaRepository

interface UserAccountRepository : JpaRepository<UserAccount, Long> {

    fun findByLoginId(loginId: String): UserAccount?
}
