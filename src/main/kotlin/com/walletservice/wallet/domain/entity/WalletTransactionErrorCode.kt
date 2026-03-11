package com.walletservice.wallet.domain.entity

enum class WalletTransactionErrorCode(
    val code: String,
) {
    INSUFFICIENT_BALANCE("잔액_부족"),
}
