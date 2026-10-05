package com.eoehd1ek.tech.common.presentation

import com.eoehd1ek.tech.common.presentation.exception.BusinessException
import com.eoehd1ek.tech.common.presentation.response.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
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
    private val internalServerErrorResponse = ErrorResponse(
        code = "INTERNAL_SERVER_ERROR",
        message = "서버 내부 오류가 발생했습니다.",
    )

    @ExceptionHandler(
        MethodArgumentTypeMismatchException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentNotValidException::class,
    )
    fun handleInvalidRequest(): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest()
            .body(invalidRequestResponse)

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(exception: HandlerMethodValidationException): ResponseEntity<ErrorResponse> =
        if (exception.isForReturnValue) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(internalServerErrorResponse)
        } else {
            ResponseEntity.badRequest()
                .body(invalidRequestResponse)
        }

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(exception: BusinessException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(exception.status)
            .body(ErrorResponse(code = exception.code, message = exception.message))

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(exception: Exception): ResponseEntity<ErrorResponse> {
        // Preserve Spring's protocol error handling, including 405 and 415.
        if (exception is org.springframework.web.ErrorResponse) throw exception
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(internalServerErrorResponse)
    }
}
