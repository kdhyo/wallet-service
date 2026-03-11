package com.walletservice.wallet.api

import com.walletservice.wallet.api.dto.GetWalletTransactionsRequest
import com.walletservice.wallet.api.dto.GetWalletTransactionsResponse
import com.walletservice.wallet.domain.service.WalletTransactionQueryService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/wallets")
class WalletTransactionQueryController(
    private val walletTransactionQueryService: WalletTransactionQueryService,
) {

    @GetMapping("/{walletId}/transactions")
    fun getTransactions(
        @PathVariable walletId: String,
        @RequestParam(name = "limit", defaultValue = "20")
        @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
        @Max(value = 100, message = "limit는 100 이하여야 합니다.")
        limit: Int,
        @RequestParam(name = "cursor", required = false) cursor: Long?,
    ): ResponseEntity<GetWalletTransactionsResponse> {
        val response = walletTransactionQueryService.getTransactions(
            GetWalletTransactionsRequest(
                walletId = walletId,
                limit = limit,
                cursor = cursor,
            ),
        )
        return ResponseEntity.ok(response)
    }
}
