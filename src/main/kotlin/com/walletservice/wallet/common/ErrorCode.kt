package com.walletservice.wallet.common

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val code: String,
    val httpStatus: HttpStatus,
    val defaultMessage: String,
) {
    INVALID_REQUEST("잘못된_요청", HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    WALLET_NOT_FOUND("월렛_없음", HttpStatus.NOT_FOUND, "월렛을 찾을 수 없습니다."),
    INSUFFICIENT_BALANCE("잔액_부족", HttpStatus.CONFLICT, "잔액이 부족합니다."),
    IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST(
        "멱등키_요청불일치",
        HttpStatus.CONFLICT,
        "동일 transactionId에 다른 요청이 전달되었습니다.",
    ),
    INTERNAL_ERROR("내부_오류", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
}
