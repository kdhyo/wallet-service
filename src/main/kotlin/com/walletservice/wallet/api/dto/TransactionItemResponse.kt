package com.walletservice.wallet.api.dto

import java.time.LocalDateTime

data class TransactionItemResponse(
    val transactionId: String,
    val walletId: String,
    val transactionType: String,
    val amount: Long,
    val balance: Long,
    val transactionDate: LocalDateTime,
    val status: String,
    val errorCode: String?,
)
