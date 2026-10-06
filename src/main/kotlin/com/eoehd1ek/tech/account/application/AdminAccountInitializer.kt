package com.eoehd1ek.tech.account.application

import com.eoehd1ek.tech.account.domain.AccountRole
import com.eoehd1ek.tech.account.domain.UserAccount
import com.eoehd1ek.tech.account.infrastructure.persistence.UserAccountRepository
import com.eoehd1ek.tech.config.properties.AdminProperties
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AdminAccountInitializer(
    private val userAccountRepository: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val adminProperties: AdminProperties,
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        val loginId = adminProperties.loginId
        check(loginId.isNotBlank() && loginId.length <= 200) {
            "Administrator login ID must be nonblank and at most 200 characters."
        }

        val existingAccount = userAccountRepository.findByLoginId(loginId)
        if (existingAccount != null) {
            check(existingAccount.role == AccountRole.ADMIN) {
                "Administrator login ID is already used by a non-administrator account."
            }
            return
        }

        val password = adminProperties.password
        check(
            password.isNotBlank() &&
                    password.toByteArray(Charsets.UTF_8).size <= 72
        ) {
            "A new administrator password must be nonblank and at most 72 UTF-8 bytes."
        }
        userAccountRepository
            .save(
                UserAccount(
                    loginId,
                    checkNotNull(passwordEncoder.encode(password)),
                    AccountRole.ADMIN
                )
            )
    }
}
