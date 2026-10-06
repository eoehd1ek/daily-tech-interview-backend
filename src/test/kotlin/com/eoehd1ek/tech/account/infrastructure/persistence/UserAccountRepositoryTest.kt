package com.eoehd1ek.tech.account.infrastructure.persistence

import com.eoehd1ek.tech.account.domain.AccountRole
import com.eoehd1ek.tech.account.domain.UserAccount
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException

@DataJpaTest
class UserAccountRepositoryTest {
    @Autowired
    private lateinit var userAccountRepository: UserAccountRepository

    @Test
    fun `로그인 ID로 저장한 계정의 필드와 생성된 ID를 조회한다`() {
        // given
        val account = userAccountRepository.save(UserAccount("repository-admin", "synthetic-hash", AccountRole.ADMIN))
        userAccountRepository.save(UserAccount("repository-user", "other-synthetic-hash", AccountRole.USER))

        // when
        val result = userAccountRepository.findByLoginId(account.loginId)

        // then
        assertThat(account.id).isNotNull().isPositive()
        assertThat(result).usingRecursiveComparison().isEqualTo(account)
    }

    @Test
    fun `존재하지 않는 로그인 ID는 null을 반환한다`() {
        // given
        userAccountRepository.save(UserAccount("existing-repository-user", "synthetic-hash", AccountRole.USER))

        // when
        val result = userAccountRepository.findByLoginId("missing-repository-user")

        // then
        assertThat(result).isNull()
    }

    @Test
    fun `역할이 달라도 같은 로그인 ID의 계정을 중복 저장할 수 없다`() {
        // given
        val loginId = "duplicate-repository-user"
        userAccountRepository.save(UserAccount(loginId, "synthetic-hash", AccountRole.USER))
        val duplicate = UserAccount(loginId, "other-synthetic-hash", AccountRole.ADMIN)

        // when
        val action = { userAccountRepository.save(duplicate) }

        // then
        assertThatThrownBy { action() }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
