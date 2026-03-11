package com.walletservice.wallet.api

import com.walletservice.wallet.api.dto.WithdrawRequest
import com.walletservice.wallet.api.dto.WithdrawResponse
import com.walletservice.wallet.domain.service.WalletWithdrawalService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/wallets")
class WalletWithdrawalController(
    private val walletWithdrawalService: WalletWithdrawalService,
) {

    @PostMapping("/{walletId}/withdrawals")
    fun withdraw(
        @PathVariable walletId: String,
        @Valid @RequestBody request: WithdrawRequest,
    ): ResponseEntity<WithdrawResponse> {
        val response = walletWithdrawalService.withdraw(walletId, request)
        return ResponseEntity.ok(response)
    }
}
