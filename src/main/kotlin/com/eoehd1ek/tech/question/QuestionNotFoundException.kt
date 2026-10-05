package com.eoehd1ek.tech.question

import com.eoehd1ek.tech.common.presentation.exception.ApplicationException
import com.eoehd1ek.tech.common.presentation.exception.ErrorType

class QuestionNotFoundException : ApplicationException(
    type = ErrorType.NOT_FOUND,
    code = "QUESTION_NOT_FOUND",
    message = "질문을 찾을 수 없습니다.",
)
