package com.eoehd1ek.tech.auth.presentation

import com.eoehd1ek.tech.auth.infrastructure.AccountPrincipal
import com.eoehd1ek.tech.auth.presentation.request.LoginRequest
import com.eoehd1ek.tech.auth.presentation.response.CsrfResponse
import com.eoehd1ek.tech.auth.presentation.response.CurrentAccountResponse
import com.eoehd1ek.tech.common.presentation.response.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy
import org.springframework.security.web.csrf.CsrfLogoutHandler
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val contextRepository: HttpSessionSecurityContextRepository,
    private val csrfRepository: HttpSessionCsrfTokenRepository,
) {

    @GetMapping("/csrf")
    fun csrf(token: CsrfToken): CsrfResponse =
        CsrfResponse(token.headerName, token.parameterName, token.token)

    @PostMapping("/login")
    fun login(
        @Valid
        @RequestBody
        body: LoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<*> {
        val authentication = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                    body.loginId,
                    body.password
                )
            )
        } catch (_: AuthenticationException) {
            return ResponseEntity
                .status(401)
                .body(ErrorResponse("INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다."))
        }

        // Programmatic JSON login must explicitly rotate and persist the authenticated session.
        ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response)
        CsrfAuthenticationStrategy(csrfRepository).onAuthentication(authentication, request, response)

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        contextRepository.saveContext(context, request, response)

        return ResponseEntity.ok(
            CurrentAccountResponse.from(
                authentication.principal as AccountPrincipal
            )
        )
    }

    @GetMapping("/me")
    fun me(
        @AuthenticationPrincipal
        principal: AccountPrincipal
    ): CurrentAccountResponse =
        CurrentAccountResponse.from(principal)

    @PostMapping("/logout")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ): ResponseEntity<Void> {
        CsrfLogoutHandler(csrfRepository).logout(request, response, authentication)
        SecurityContextLogoutHandler().logout(request, response, authentication)
        CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication)
        return ResponseEntity
            .noContent()
            .build()
    }
}
