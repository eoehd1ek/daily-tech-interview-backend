package com.eoehd1ek.tech.account.domain

import jakarta.persistence.*

@Entity
@Table(name = "app_user")
class UserAccount(
    loginId: String,
    passwordHash: String,
    role: AccountRole,
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @field:Column(name = "login_id", nullable = false, unique = true)
    var loginId: String = loginId
        protected set

    @field:Column(name = "password_hash", nullable = false)
    var passwordHash: String = passwordHash
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var role: AccountRole = role
        protected set
}
