package com.walletservice.wallet.api.dto

import java.time.LocalDateTime

data class WithdrawResponse(
    val transactionId: String,
    val walletId: String,
    val amount: Long,
    val balanceAfter: Long?,
    val status: String,
    val errorCode: String?,
    val processedAt: LocalDateTime?,
)
