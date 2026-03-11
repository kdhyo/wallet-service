package com.walletservice.wallet.domain.service

import com.walletservice.wallet.api.dto.GetWalletTransactionsRequest
import com.walletservice.wallet.api.dto.GetWalletTransactionsResponse
import com.walletservice.wallet.api.dto.PageInfoResponse
import com.walletservice.wallet.api.dto.TransactionItemResponse
import com.walletservice.wallet.common.ApiException
import com.walletservice.wallet.common.ErrorCode
import com.walletservice.wallet.domain.entity.WalletTransactionEntity
import com.walletservice.wallet.domain.entity.WalletTransactionStatus
import com.walletservice.wallet.domain.repository.WalletRepository
import com.walletservice.wallet.domain.repository.WalletTransactionRepository
import com.walletservice.wallet.domain.util.toClientLong
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WalletTransactionQueryService(
    private val walletRepository: WalletRepository,
    private val walletTransactionRepository: WalletTransactionRepository,
) {

    @Transactional(readOnly = true)
    fun getTransactions(request: GetWalletTransactionsRequest): GetWalletTransactionsResponse {
        val internalWalletId = getWalletIdOrThrow(request.walletId)
        val rows = getRows(internalWalletId, request.cursor, request.limit)

        val hasNext = rows.size > request.limit
        val sliced = if (hasNext) rows.take(request.limit) else rows
        val nextCursor = if (hasNext) {
            val tail = sliced.last()
            tail.requireId()
        } else {
            null
        }

        return GetWalletTransactionsResponse(
            walletId = request.walletId,
            transactions = sliced.map { it.toItemResponse(request.walletId) },
            pageInfo = PageInfoResponse(
                limit = request.limit,
                hasNext = hasNext,
                nextCursor = nextCursor,
            ),
        )
    }

    private fun getWalletIdOrThrow(publicWalletId: String): Long {
        val wallet = walletRepository.findByPublicWalletId(publicWalletId)
            ?: throw ApiException(ErrorCode.WALLET_NOT_FOUND)
        return requireNotNull(wallet.id)
    }

    private fun getRows(walletId: Long, cursorId: Long?, limit: Int): List<WalletTransactionEntity> {
        val pageRequest = PageRequest.of(0, limit + 1)
        return if (cursorId == null) {
            walletTransactionRepository.findByWalletIdAndStatusInOrderByIdDesc(
                walletId = walletId,
                completedStatuses = WalletTransactionStatus.COMPLETED,
                pageable = pageRequest,
            )
        } else {
            walletTransactionRepository.findByWalletIdAndStatusInAndIdLessThanOrderByIdDesc(
                walletId = walletId,
                completedStatuses = WalletTransactionStatus.COMPLETED,
                cursorId = cursorId,
                pageable = pageRequest,
            )
        }
    }

    private fun WalletTransactionEntity.toItemResponse(walletId: String): TransactionItemResponse {
        return TransactionItemResponse(
            transactionId = transactionId,
            walletId = walletId,
            transactionType = transactionType.name,
            amount = amount.toClientLong(),
            balance = requireNotNull(balanceAfter).toClientLong(),
            transactionDate = requireNotNull(processedAt),
            status = status.name,
            errorCode = errorCode?.code,
        )
    }
}

private fun WalletTransactionEntity.requireId(): Long {
    return requireNotNull(id) { "walletTransaction.id는 null일 수 없습니다." }
}
