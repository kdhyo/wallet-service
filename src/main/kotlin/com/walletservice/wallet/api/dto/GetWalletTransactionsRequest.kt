package com.walletservice.wallet.api.dto

data class GetWalletTransactionsRequest(
    val walletId: String,
    val limit: Int = 20,
    val cursor: Long? = null,
)
