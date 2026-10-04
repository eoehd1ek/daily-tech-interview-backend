package com.eoehd1ek.tech.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val invalidRequestResponse = ErrorResponse(
        code = "INVALID_REQUEST",
        message = "요청 값의 형식이나 범위가 올바르지 않습니다.",
    )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleInvalidRequest(): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest()
            .body(invalidRequestResponse)

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(exception: HandlerMethodValidationException): ResponseEntity<ErrorResponse> =
        if (exception.isForReturnValue) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."))
        } else {
            ResponseEntity.badRequest()
                .body(invalidRequestResponse)
        }

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(exception: BusinessException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(exception.status)
            .body(ErrorResponse(code = exception.code, message = exception.message))
}
