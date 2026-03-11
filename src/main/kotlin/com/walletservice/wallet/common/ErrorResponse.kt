package com.walletservice.wallet.common

import java.time.LocalDateTime

data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: LocalDateTime,
    val path: String,
)
