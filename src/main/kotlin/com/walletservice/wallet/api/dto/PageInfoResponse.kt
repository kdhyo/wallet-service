package com.walletservice.wallet.api.dto

data class PageInfoResponse(
    val limit: Int,
    val hasNext: Boolean,
    val nextCursor: Long?,
)
