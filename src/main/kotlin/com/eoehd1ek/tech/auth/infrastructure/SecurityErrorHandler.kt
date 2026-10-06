package com.eoehd1ek.tech.auth.infrastructure

import com.eoehd1ek.tech.common.presentation.response.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.csrf.CsrfException
import tools.jackson.databind.ObjectMapper

class SecurityErrorHandler(private val mapper: ObjectMapper) : AuthenticationEntryPoint, AccessDeniedHandler {

    override fun commence(request: HttpServletRequest, response: HttpServletResponse, exception: AuthenticationException) {
        write(response, 401, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.")
    }

    override fun handle(request: HttpServletRequest, response: HttpServletResponse, exception: AccessDeniedException) {
        if (exception is CsrfException) {
            write(response, 403, "INVALID_CSRF_TOKEN", "요청 보호 토큰이 유효하지 않습니다.")
        } else {
            write(response, 403, "ACCESS_DENIED", "접근 권한이 없습니다.")
        }
    }

    private fun write(response: HttpServletResponse, status: Int, code: String, message: String) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        mapper.writeValue(response.outputStream, ErrorResponse(code, message))
    }
}
