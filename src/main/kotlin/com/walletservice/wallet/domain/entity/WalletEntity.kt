package com.walletservice.wallet.domain.entity

import java.math.BigDecimal
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "wallet")
class WalletEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "public_wallet_id", nullable = false, unique = true, length = 30)
    var publicWalletId: String,

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    var balance: BigDecimal,
) : BaseTimeEntity()
