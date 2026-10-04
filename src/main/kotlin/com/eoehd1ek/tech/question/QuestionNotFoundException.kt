package com.eoehd1ek.tech.question

import com.eoehd1ek.tech.common.BusinessException
import org.springframework.http.HttpStatus

class QuestionNotFoundException : BusinessException(
    status = HttpStatus.NOT_FOUND,
    code = "QUESTION_NOT_FOUND",
    message = "질문을 찾을 수 없습니다.",
)
