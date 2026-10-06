package com.eoehd1ek.tech.account.application

import com.eoehd1ek.tech.account.domain.AccountRole
import com.eoehd1ek.tech.account.domain.UserAccount
import com.eoehd1ek.tech.account.infrastructure.persistence.UserAccountRepository
import com.eoehd1ek.tech.config.properties.AdminProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.security.crypto.password.PasswordEncoder

@ExtendWith(MockitoExtension::class)
class AdminAccountInitializerTest {
    @Mock
    private lateinit var userAccountRepository: UserAccountRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @Captor
    private lateinit var accountCaptor: ArgumentCaptor<UserAccount>

    @Test
    fun `신규 관리자는 비밀번호를 인코딩하여 ADMIN 계정으로 저장한다`() {
        // given
        val properties = AdminProperties("bootstrap-admin", " synthetic password ")
        val initializer = AdminAccountInitializer(userAccountRepository, passwordEncoder, properties)
        given(userAccountRepository.findByLoginId(properties.loginId)).willReturn(null)
        given(passwordEncoder.encode(properties.password)).willReturn("synthetic-hash")

        // when
        initializer.run(DefaultApplicationArguments())

        // then
        verify(passwordEncoder).encode(properties.password)
        verify(userAccountRepository).save(accountCaptor.capture())
        assertThat(accountCaptor.value.id).isNull()
        assertThat(accountCaptor.value.loginId).isEqualTo(properties.loginId)
        assertThat(accountCaptor.value.passwordHash).isEqualTo("synthetic-hash")
        assertThat(accountCaptor.value.role).isEqualTo(AccountRole.ADMIN)
    }

    @Test
    fun `기존 ADMIN은 비밀번호 설정이 없어도 해시를 유지하고 저장하지 않는다`() {
        // given
        val properties = AdminProperties("bootstrap-admin")
        val existing = UserAccount(properties.loginId, "existing-hash", AccountRole.ADMIN)
        val initializer = AdminAccountInitializer(userAccountRepository, passwordEncoder, properties)
        given(userAccountRepository.findByLoginId(properties.loginId)).willReturn(existing)

        // when
        initializer.run(DefaultApplicationArguments())

        // then
        assertThat(existing.passwordHash).isEqualTo("existing-hash")
        verify(userAccountRepository).findByLoginId(properties.loginId)
        verifyNoMoreInteractions(userAccountRepository)
        verifyNoInteractions(passwordEncoder)
    }

    @Test
    fun `기존 ADMIN은 길이 제한을 초과한 새 비밀번호 설정도 검증하거나 반영하지 않는다`() {
        // given
        val properties = AdminProperties("bootstrap-admin", "x".repeat(73))
        val existing = UserAccount(properties.loginId, "existing-hash", AccountRole.ADMIN)
        val initializer = AdminAccountInitializer(userAccountRepository, passwordEncoder, properties)
        given(userAccountRepository.findByLoginId(properties.loginId)).willReturn(existing)

        // when
        initializer.run(DefaultApplicationArguments())

        // then
        assertThat(existing.passwordHash).isEqualTo("existing-hash")
        verify(userAccountRepository).findByLoginId(properties.loginId)
        verifyNoMoreInteractions(userAccountRepository)
        verifyNoInteractions(passwordEncoder)
    }

    @Test
    fun `같은 로그인 ID의 USER가 있으면 비밀값 없는 예외로 중단한다`() {
        // given
        val properties = AdminProperties("bootstrap-admin", "synthetic-password")
        val existing = UserAccount(properties.loginId, "synthetic-existing-hash", AccountRole.USER)
        val initializer = AdminAccountInitializer(userAccountRepository, passwordEncoder, properties)
        given(userAccountRepository.findByLoginId(properties.loginId)).willReturn(existing)

        // when
        val action = { initializer.run(DefaultApplicationArguments()) }

        // then
        assertThatThrownBy { action() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Administrator login ID is already used by a non-administrator account.")
        assertThat(existing.role).isEqualTo(AccountRole.USER)
        assertThat(existing.passwordHash).isEqualTo("synthetic-existing-hash")
        verify(userAccountRepository).findByLoginId(properties.loginId)
        verifyNoMoreInteractions(userAccountRepository)
        verifyNoInteractions(passwordEncoder)
    }

    @Test
    fun `기본값 또는 공백 로그인 ID는 조회 전에 거부한다`() {
        // given
        val properties = listOf(AdminProperties(), AdminProperties(" \t\n", "synthetic-password"))
        val initializers = properties.map { AdminAccountInitializer(userAccountRepository, passwordEncoder, it) }

        // when
        val actions = initializers.map { initializer -> { initializer.run(DefaultApplicationArguments()) } }

        // then
        actions.forEach { action ->
            assertThatThrownBy { action() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("Administrator login ID must be nonblank and at most 200 characters.")
        }
        verifyNoInteractions(userAccountRepository, passwordEncoder)
    }

    @Test
    fun `로그인 ID가 200자를 초과하면 조회 전에 거부한다`() {
        // given
        val properties = AdminProperties("a".repeat(201), "synthetic-password")
        val initializer = AdminAccountInitializer(userAccountRepository, passwordEncoder, properties)

        // when
        val action = { initializer.run(DefaultApplicationArguments()) }

        // then
        assertThatThrownBy { action() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Administrator login ID must be nonblank and at most 200 characters.")
        verifyNoInteractions(userAccountRepository, passwordEncoder)
    }

    @Test
    fun `신규 관리자 비밀번호가 비어 있거나 공백이면 인코딩과 저장을 하지 않는다`() {
        // given
        val properties = listOf(AdminProperties("bootstrap-admin"), AdminProperties("bootstrap-admin", " \t\n"))
        val initializers = properties.map { AdminAccountInitializer(userAccountRepository, passwordEncoder, it) }
        given(userAccountRepository.findByLoginId("bootstrap-admin")).willReturn(null)

        // when
        val actions = initializers.map { initializer -> { initializer.run(DefaultApplicationArguments()) } }

        // then
        actions.forEach { action ->
            assertThatThrownBy { action() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("A new administrator password must be nonblank and at most 72 UTF-8 bytes.")
        }
        verify(userAccountRepository, times(2)).findByLoginId("bootstrap-admin")
        verifyNoMoreInteractions(userAccountRepository)
        verifyNoInteractions(passwordEncoder)
    }

    @Test
    fun `신규 비밀번호는 문자 수가 아닌 UTF8 72바이트 초과를 거부한다`() {
        // given
        val properties = AdminProperties("bootstrap-admin", "가".repeat(25))
        val initializer = AdminAccountInitializer(userAccountRepository, passwordEncoder, properties)
        given(userAccountRepository.findByLoginId(properties.loginId)).willReturn(null)

        // when
        val action = { initializer.run(DefaultApplicationArguments()) }

        // then
        assertThat(properties.password.length).isLessThan(72)
        assertThatThrownBy { action() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("A new administrator password must be nonblank and at most 72 UTF-8 bytes.")
        verify(userAccountRepository).findByLoginId(properties.loginId)
        verifyNoMoreInteractions(userAccountRepository)
        verifyNoInteractions(passwordEncoder)
    }

    @Test
    fun `로그인 ID 200자와 신규 비밀번호 UTF8 72바이트는 허용한다`() {
        // given
        val properties = AdminProperties("a".repeat(200), "가".repeat(24))
        val initializer = AdminAccountInitializer(userAccountRepository, passwordEncoder, properties)
        given(userAccountRepository.findByLoginId(properties.loginId)).willReturn(null)
        given(passwordEncoder.encode(properties.password)).willReturn("synthetic-hash")

        // when
        initializer.run(DefaultApplicationArguments())

        // then
        verify(passwordEncoder).encode(properties.password)
        verify(userAccountRepository).save(accountCaptor.capture())
        assertThat(accountCaptor.value.loginId).isEqualTo(properties.loginId)
        assertThat(accountCaptor.value.passwordHash).isEqualTo("synthetic-hash")
        assertThat(accountCaptor.value.role).isEqualTo(AccountRole.ADMIN)
    }
}
