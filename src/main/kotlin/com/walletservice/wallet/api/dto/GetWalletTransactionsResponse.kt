package com.walletservice.wallet.api.dto

data class GetWalletTransactionsResponse(
    val walletId: String,
    val transactions: List<TransactionItemResponse>,
    val pageInfo: PageInfoResponse,
)
