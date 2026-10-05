package com.eoehd1ek.tech.evaluation.infrastructure.llm

class InvalidLlmResponseException(cause: Throwable? = null) :
    RuntimeException("LLM 응답이 평가 계약과 일치하지 않습니다.", cause)
