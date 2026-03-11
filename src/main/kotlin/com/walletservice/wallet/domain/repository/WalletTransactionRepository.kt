package com.walletservice.wallet.domain.repository

import com.walletservice.wallet.domain.entity.WalletTransactionEntity
import com.walletservice.wallet.domain.entity.WalletTransactionStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface WalletTransactionRepository : JpaRepository<WalletTransactionEntity, Long> {
    fun findByTransactionId(transactionId: String): WalletTransactionEntity?

    fun findByWalletIdAndStatusInOrderByIdDesc(
        walletId: Long,
        completedStatuses: Collection<WalletTransactionStatus>,
        pageable: Pageable,
    ): List<WalletTransactionEntity>

    fun findByWalletIdAndStatusInAndIdLessThanOrderByIdDesc(
        walletId: Long,
        completedStatuses: Collection<WalletTransactionStatus>,
        cursorId: Long,
        pageable: Pageable,
    ): List<WalletTransactionEntity>
}
