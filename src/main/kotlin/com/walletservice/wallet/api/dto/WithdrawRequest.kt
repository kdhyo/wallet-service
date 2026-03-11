package com.walletservice.wallet.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class WithdrawRequest(
    @field:NotBlank(message = "transactionId는 필수입니다.")
    val transactionId: String,
    @field:Positive(message = "amount는 0보다 커야 합니다.")
    val amount: Long,
)
