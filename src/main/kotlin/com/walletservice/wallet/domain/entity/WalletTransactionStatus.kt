package com.walletservice.wallet.domain.entity

enum class WalletTransactionStatus {
    PROCESSING,
    SUCCESS,
    FAILED,
    ;

    companion object {
        val COMPLETED: Set<WalletTransactionStatus> = setOf(SUCCESS, FAILED)
    }
}
