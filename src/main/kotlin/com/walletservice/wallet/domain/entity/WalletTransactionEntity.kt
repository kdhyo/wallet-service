package com.walletservice.wallet.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "wallet_transaction")
class WalletTransactionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "transaction_id", nullable = false, unique = true, length = 128)
    var transactionId: String,

    @Column(name = "wallet_id", nullable = false)
    var walletId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 16)
    var transactionType: WalletTransactionType,

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    var amount: BigDecimal,

    @Column(name = "balance_after", precision = 19, scale = 4)
    var balanceAfter: BigDecimal? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: WalletTransactionStatus,

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 64)
    var errorCode: WalletTransactionErrorCode? = null,

    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null,
) : BaseTimeEntity() {
    fun markSuccess(balanceAfter: BigDecimal, processedAt: LocalDateTime = LocalDateTime.now()) {
        status = WalletTransactionStatus.SUCCESS
        this.balanceAfter = balanceAfter
        errorCode = null
        this.processedAt = processedAt
    }

    fun markFailed(
        errorCode: WalletTransactionErrorCode,
        balanceAfter: BigDecimal,
        processedAt: LocalDateTime = LocalDateTime.now(),
    ) {
        status = WalletTransactionStatus.FAILED
        this.balanceAfter = balanceAfter
        this.errorCode = errorCode
        this.processedAt = processedAt
    }
}
