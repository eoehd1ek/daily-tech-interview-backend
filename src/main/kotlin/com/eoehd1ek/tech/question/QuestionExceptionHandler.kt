package com.eoehd1ek.tech.question

import com.eoehd1ek.tech.common.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice(assignableTypes = [QuestionController::class])
class QuestionExceptionHandler {
    @ExceptionHandler(
        MethodArgumentTypeMismatchException::class,
        HandlerMethodValidationException::class,
    )
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidRequest(): ErrorResponse = ErrorResponse(
        code = "INVALID_REQUEST",
        message = "질문 ID는 1 이상 9,007,199,254,740,991 이하의 정수여야 합니다.",
    )

    @ExceptionHandler(QuestionNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleQuestionNotFound(): ErrorResponse = ErrorResponse(
        code = "QUESTION_NOT_FOUND",
        message = "질문을 찾을 수 없습니다.",
    )
}
