package com.walletservice.wallet.domain.repository

import com.walletservice.wallet.domain.entity.WalletEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface WalletRepository : JpaRepository<WalletEntity, Long> {
    fun findByPublicWalletId(publicWalletId: String): WalletEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findOneByPublicWalletId(publicWalletId: String): WalletEntity?
}
