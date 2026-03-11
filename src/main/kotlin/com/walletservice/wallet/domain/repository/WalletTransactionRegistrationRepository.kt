package com.walletservice.wallet.domain.repository

import java.math.BigDecimal
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class WalletTransactionRegistrationRepository(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) {

    fun registerProcessingWithdrawalTransaction(
        transactionId: String,
        walletId: Long,
        amount: BigDecimal,
    ): Int {
        return namedParameterJdbcTemplate.update(
            SQL_REGISTER_PROCESSING_WITHDRAWAL,
            MapSqlParameterSource()
                .addValue("transactionId", transactionId)
                .addValue("walletId", walletId)
                .addValue("amount", amount),
        )
    }

    companion object {
        private const val SQL_REGISTER_PROCESSING_WITHDRAWAL = """
            INSERT INTO wallet_transaction (
                transaction_id,
                wallet_id,
                transaction_type,
                amount
            ) VALUES (
                :transactionId,
                :walletId,
                'WITHDRAW',
                :amount
            )
        """
    }
}
