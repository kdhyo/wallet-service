package com.walletservice.wallet.domain.service

import com.walletservice.wallet.api.dto.WithdrawRequest
import com.walletservice.wallet.api.dto.WithdrawResponse
import com.walletservice.wallet.common.ApiException
import com.walletservice.wallet.common.ErrorCode
import com.walletservice.wallet.common.InsufficientBalanceException
import com.walletservice.wallet.domain.entity.*
import com.walletservice.wallet.domain.repository.WalletRepository
import com.walletservice.wallet.domain.repository.WalletTransactionRegistrationRepository
import com.walletservice.wallet.domain.repository.WalletTransactionRepository
import com.walletservice.wallet.domain.util.toClientLong
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class WalletWithdrawalService(
    private val walletRepository: WalletRepository,
    private val walletTransactionRegistrationRepository: WalletTransactionRegistrationRepository,
    private val walletTransactionRepository: WalletTransactionRepository,
) {

    @Transactional(noRollbackFor = [InsufficientBalanceException::class])
    fun withdraw(walletId: String, request: WithdrawRequest): WithdrawResponse {
        val lockedWallet =
            walletRepository.findOneByPublicWalletId(walletId) ?: throw ApiException(ErrorCode.WALLET_NOT_FOUND)
        val internalWalletId = lockedWallet.requireId()
        val requestAmount = request.amount.toBigDecimal()
        val registrationResult =
            registerProcessingWithdrawal(request.transactionId, internalWalletId, requestAmount, walletId)

        val transaction = findTransactionOrThrow(request.transactionId)
        if (registrationResult == TransactionRegistrationResult.DUPLICATE) {
            validateIdempotentReplay(transaction, internalWalletId, requestAmount)
            return handleDuplicateTransaction(transaction, walletId)
        }

        processRegisteredTransaction(lockedWallet, transaction, requestAmount)
        return transaction.toWithdrawResponse(walletId)
    }

    private fun handleDuplicateTransaction(
        transaction: WalletTransactionEntity,
        walletId: String,
    ): WithdrawResponse {
        return when (transaction.status) {
            WalletTransactionStatus.SUCCESS -> transaction.toWithdrawResponse(walletId)
            WalletTransactionStatus.FAILED -> when (transaction.errorCode) {
                WalletTransactionErrorCode.INSUFFICIENT_BALANCE -> throw InsufficientBalanceException()
                null -> throw ApiException(ErrorCode.INTERNAL_ERROR)
            }

            WalletTransactionStatus.PROCESSING -> throw ApiException(ErrorCode.INTERNAL_ERROR)
        }
    }

    private fun registerProcessingWithdrawal(
        transactionId: String,
        walletId: Long,
        amount: BigDecimal,
        publicWalletId: String,
    ): TransactionRegistrationResult {
        return try {
            val insertedRows = walletTransactionRegistrationRepository.registerProcessingWithdrawalTransaction(
                transactionId = transactionId,
                walletId = walletId,
                amount = amount,
            )
            if (insertedRows != 1) {
                logger.error {
                    """
                        트랜잭션 선점 INSERT 결과가 비정상입니다.  
                        transactionId=$transactionId, walletId=$publicWalletId, insertedRows=$insertedRows
                    """.trimIndent()
                }
                throw ApiException(ErrorCode.INTERNAL_ERROR)
            }
            TransactionRegistrationResult.REGISTERED
        } catch (ex: DuplicateKeyException) {
            logger.debug {
                """
                    중복 transactionId 요청을 멱등 응답으로 처리합니다. 
                    transactionId=$transactionId, walletId=$publicWalletId
                """.trimIndent()
            }
            TransactionRegistrationResult.DUPLICATE
        } catch (ex: DataAccessException) {
            logger.error(ex) {
                """
                    트랜잭션 선점 중 DB 접근 예외가 발생했습니다. 
                    transactionId=$transactionId, walletId=$publicWalletId, 
                    예외타입=${ex.javaClass.name}, 예외메시지=${ex.message}
                """.trimIndent()
            }
            throw ApiException(ErrorCode.INTERNAL_ERROR)
        }
    }

    private fun findTransactionOrThrow(transactionId: String): WalletTransactionEntity {
        return walletTransactionRepository.findByTransactionId(transactionId)
            ?: throw ApiException(ErrorCode.INTERNAL_ERROR)
    }

    private fun validateIdempotentReplay(
        transaction: WalletTransactionEntity,
        walletId: Long,
        requestAmount: BigDecimal,
    ) {
        val sameWallet = transaction.walletId == walletId
        val sameType = transaction.transactionType == WalletTransactionType.WITHDRAW
        val sameAmount = transaction.amount.compareTo(requestAmount) == 0
        if (!sameWallet || !sameType || !sameAmount) {
            throw ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST)
        }
    }

    private fun processRegisteredTransaction(
        lockedWallet: WalletEntity,
        transaction: WalletTransactionEntity,
        requestAmount: BigDecimal,
    ) {
        if (lockedWallet.balance < requestAmount) {
            transaction.markFailed(
                errorCode = WalletTransactionErrorCode.INSUFFICIENT_BALANCE,
                balanceAfter = lockedWallet.balance,
            )
            throw InsufficientBalanceException()
        }

        val nextBalance = lockedWallet.balance - requestAmount
        lockedWallet.balance = nextBalance
        transaction.markSuccess(balanceAfter = nextBalance)
    }

    private fun WalletTransactionEntity.toWithdrawResponse(walletId: String): WithdrawResponse {
        val errorCodeName = when (status) {
            WalletTransactionStatus.FAILED -> errorCode?.code
            else -> null
        }
        return WithdrawResponse(
            transactionId = transactionId,
            walletId = walletId,
            amount = amount.toClientLong(),
            balanceAfter = balanceAfter?.toClientLong(),
            status = status.name,
            errorCode = errorCodeName,
            processedAt = processedAt,
        )
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

private fun WalletEntity.requireId(): Long {
    return requireNotNull(id) { "wallet.id는 null일 수 없습니다." }
}

private enum class TransactionRegistrationResult {
    REGISTERED,
    DUPLICATE,
}
