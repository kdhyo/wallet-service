package com.walletservice.wallet

import com.walletservice.wallet.api.dto.GetWalletTransactionsRequest
import com.walletservice.wallet.api.dto.WithdrawRequest
import com.walletservice.wallet.common.ApiException
import com.walletservice.wallet.common.ErrorCode
import com.walletservice.wallet.domain.entity.WalletEntity
import com.walletservice.wallet.domain.entity.WalletTransactionEntity
import com.walletservice.wallet.domain.entity.WalletTransactionErrorCode
import com.walletservice.wallet.domain.entity.WalletTransactionStatus
import com.walletservice.wallet.domain.entity.WalletTransactionType
import com.walletservice.wallet.domain.repository.WalletRepository
import com.walletservice.wallet.domain.repository.WalletTransactionRepository
import com.walletservice.wallet.domain.service.WalletTransactionQueryService
import com.walletservice.wallet.domain.service.WalletWithdrawalService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class WalletServiceIntegrationTests {

    @Autowired
    private lateinit var walletWithdrawalService: WalletWithdrawalService

    @Autowired
    private lateinit var walletTransactionQueryService: WalletTransactionQueryService

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @Autowired
    private lateinit var walletTransactionRepository: WalletTransactionRepository

    @BeforeEach
    fun 테스트데이터초기화() {
        walletTransactionRepository.deleteAll()
        walletRepository.deleteAll()
    }

    @Test
    fun `동일 transactionId와 동일 payload는 100개 동시 요청에서도 1회만 반영된다`() {
        val wallet = walletRepository.save(
            WalletEntity(
                publicWalletId = "wlt_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                balance = BigDecimal("10000.00"),
            ),
        )

        val request = WithdrawRequest(transactionId = "txn-same-1", amount = 1000L)
        val results = runConcurrently(100) {
            walletWithdrawalService.withdraw(wallet.publicWalletId, request)
        }

        val updatedWallet = walletRepository.findById(wallet.id!!).orElseThrow()
        val savedTransactions = walletTransactionRepository.findAll()

        assertThat(results).hasSize(100)
        assertThat(results.map { it.status }.distinct()).containsExactly("SUCCESS")
        assertThat(updatedWallet.balance).isEqualByComparingTo("9000.00")
        assertThat(savedTransactions).hasSize(1)
    }

    @Test
    fun `동일 transactionId와 다른 payload는 409를 반환한다`() {
        val wallet = walletRepository.save(
            WalletEntity(
                publicWalletId = "wlt_01ARZ3NDEKTSV4RRFFQ69G5FB0",
                balance = BigDecimal("10000.00"),
            ),
        )

        walletWithdrawalService.withdraw(wallet.publicWalletId, WithdrawRequest("txn-diff-1", 1000L))

        assertThatThrownBy {
            walletWithdrawalService.withdraw(wallet.publicWalletId, WithdrawRequest("txn-diff-1", 2000L))
        }.isInstanceOf(ApiException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST)
    }

    @Test
    fun `서로 다른 transactionId로 100개 동시 출금해도 총 출금액은 초기 잔액을 초과하지 않는다`() {
        val wallet = walletRepository.save(
            WalletEntity(
                publicWalletId = "wlt_01ARZ3NDEKTSV4RRFFQ69G5FB9",
                balance = BigDecimal("500000.00"),
            ),
        )

        val outcomes = runConcurrently(100) { index ->
            val req = WithdrawRequest(transactionId = "txn-spec-$index", amount = 10000L)
            runCatching { walletWithdrawalService.withdraw(wallet.publicWalletId, req) }
        }

        val updatedWallet = walletRepository.findById(wallet.id!!).orElseThrow()
        val txs = walletTransactionRepository.findAll()
            .filter { it.transactionId.startsWith("txn-spec-") }
        val successCount = txs.count { it.status == WalletTransactionStatus.SUCCESS }
        val successAmount = BigDecimal("10000.00").multiply(BigDecimal.valueOf(successCount.toLong()))

        val failures = outcomes.mapNotNull { it.exceptionOrNull() }
        assertThat(outcomes).hasSize(100)
        assertThat(failures).isNotEmpty
        assertThat(failures).allSatisfy { ex ->
            assertThat(ex).isInstanceOf(ApiException::class.java)
            val apiException = ex as ApiException
            assertThat(apiException.errorCode).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE)
        }
        assertThat(updatedWallet.balance).isGreaterThanOrEqualTo(BigDecimal.ZERO)
        assertThat(successAmount).isLessThanOrEqualTo(BigDecimal("500000.00"))
        assertThat(updatedWallet.balance).isEqualByComparingTo(BigDecimal("500000.00").subtract(successAmount))
    }

    @Test
    fun `서로 다른 transactionId 동시 처리에서도 잔액은 음수가 되지 않는다`() {
        val wallet = walletRepository.save(
            WalletEntity(
                publicWalletId = "wlt_01ARZ3NDEKTSV4RRFFQ69G5FB1",
                balance = BigDecimal("100000.00"),
            ),
        )

        val outcomes = runConcurrently(100) { index ->
            val req = WithdrawRequest(transactionId = "txn-unique-$index", amount = 2000L)
            runCatching { walletWithdrawalService.withdraw(wallet.publicWalletId, req) }
        }

        val updatedWallet = walletRepository.findById(wallet.id!!).orElseThrow()
        val txs = walletTransactionRepository.findAll()
        val successCount = txs.count { it.status == WalletTransactionStatus.SUCCESS }
        val successAmount = BigDecimal("2000.00").multiply(BigDecimal.valueOf(successCount.toLong()))

        val failures = outcomes.mapNotNull { it.exceptionOrNull() }
        assertThat(outcomes).hasSize(100)
        assertThat(failures).isNotEmpty
        assertThat(failures).allSatisfy { ex ->
            assertThat(ex).isInstanceOf(ApiException::class.java)
            val apiException = ex as ApiException
            assertThat(apiException.errorCode).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE)
        }
        assertThat(updatedWallet.balance).isGreaterThanOrEqualTo(BigDecimal.ZERO)
        assertThat(successAmount).isLessThanOrEqualTo(BigDecimal("100000.00"))
        assertThat(updatedWallet.balance).isEqualByComparingTo(BigDecimal("100000.00").subtract(successAmount))
    }

    @Test
    fun `잔액 부족 실패는 동일 요청 재시도 시 동일하게 재현된다`() {
        val wallet = walletRepository.save(
            WalletEntity(
                publicWalletId = "wlt_01ARZ3NDEKTSV4RRFFQ69G5FB2",
                balance = BigDecimal("500.00"),
            ),
        )

        val request = WithdrawRequest(transactionId = "txn-fail-replay", amount = 1000L)

        assertThatThrownBy {
            walletWithdrawalService.withdraw(wallet.publicWalletId, request)
        }.isInstanceOf(ApiException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE)

        assertThatThrownBy {
            walletWithdrawalService.withdraw(wallet.publicWalletId, request)
        }.isInstanceOf(ApiException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE)

        val updatedWallet = walletRepository.findById(wallet.id!!).orElseThrow()
        val savedTransactions = walletTransactionRepository.findAll()

        assertThat(updatedWallet.balance).isEqualByComparingTo("500.00")
        assertThat(savedTransactions).hasSize(1)
        assertThat(savedTransactions.single().status).isEqualTo(WalletTransactionStatus.FAILED)
        assertThat(savedTransactions.single().errorCode).isEqualTo(WalletTransactionErrorCode.INSUFFICIENT_BALANCE)
    }

    @Test
    fun `커서 페이징은 hasNext와 nextCursor를 일관되게 반환한다`() {
        val wallet = walletRepository.save(
            WalletEntity(
                publicWalletId = "wlt_01ARZ3NDEKTSV4RRFFQ69G5FB3",
                balance = BigDecimal("10000.00"),
            ),
        )

        walletWithdrawalService.withdraw(wallet.publicWalletId, WithdrawRequest("txn-page-1", 1000L))
        walletWithdrawalService.withdraw(wallet.publicWalletId, WithdrawRequest("txn-page-2", 1000L))
        walletWithdrawalService.withdraw(wallet.publicWalletId, WithdrawRequest("txn-page-3", 1000L))

        val firstPage = walletTransactionQueryService.getTransactions(
            GetWalletTransactionsRequest(walletId = wallet.publicWalletId, limit = 2, cursor = null),
        )

        assertThat(firstPage.transactions).hasSize(2)
        assertThat(firstPage.pageInfo.hasNext).isTrue()
        assertThat(firstPage.pageInfo.nextCursor).isNotNull()

        val secondPage = walletTransactionQueryService.getTransactions(
            GetWalletTransactionsRequest(
                walletId = wallet.publicWalletId,
                limit = 2,
                cursor = firstPage.pageInfo.nextCursor,
            ),
        )

        assertThat(secondPage.transactions).hasSize(1)
        assertThat(secondPage.pageInfo.hasNext).isFalse()
        assertThat(secondPage.pageInfo.nextCursor).isNull()
    }

    @Test
    fun `거래내역 조회는 완료 상태 SUCCESS FAILED만 반환한다`() {
        val wallet = walletRepository.save(
            WalletEntity(
                publicWalletId = "wlt_01ARZ3NDEKTSV4RRFFQ69G5FB4",
                balance = BigDecimal("10000.00"),
            ),
        )

        val internalWalletId = wallet.id!!
        walletTransactionRepository.save(
            WalletTransactionEntity(
                transactionId = "txn-complete-success",
                walletId = internalWalletId,
                transactionType = WalletTransactionType.WITHDRAW,
                amount = BigDecimal("1000.00"),
                status = WalletTransactionStatus.SUCCESS,
            ).apply {
                markSuccess(
                    balanceAfter = BigDecimal("9000.00"),
                    processedAt = LocalDateTime.now().minusSeconds(3),
                )
            },
        )
        walletTransactionRepository.save(
            WalletTransactionEntity(
                transactionId = "txn-complete-processing",
                walletId = internalWalletId,
                transactionType = WalletTransactionType.WITHDRAW,
                amount = BigDecimal("1000.00"),
                status = WalletTransactionStatus.PROCESSING,
            ),
        )
        walletTransactionRepository.save(
            WalletTransactionEntity(
                transactionId = "txn-complete-failed",
                walletId = internalWalletId,
                transactionType = WalletTransactionType.WITHDRAW,
                amount = BigDecimal("2000.00"),
                status = WalletTransactionStatus.FAILED,
            ).apply {
                markFailed(
                    errorCode = WalletTransactionErrorCode.INSUFFICIENT_BALANCE,
                    balanceAfter = BigDecimal("9000.00"),
                    processedAt = LocalDateTime.now().minusSeconds(1),
                )
            },
        )

        val response = walletTransactionQueryService.getTransactions(
            GetWalletTransactionsRequest(walletId = wallet.publicWalletId, limit = 10, cursor = null),
        )

        assertThat(response.transactions).hasSize(2)
        assertThat(response.transactions.map { it.status })
            .containsExactly("FAILED", "SUCCESS")
    }

    private fun <T> runConcurrently(taskCount: Int, task: (Int) -> T): List<T> {
        val poolSize = minOf(20, taskCount)
        val executor = Executors.newFixedThreadPool(poolSize)
        try {
            val start = CountDownLatch(1)
            val done = CountDownLatch(taskCount)
            val futures = (0 until taskCount).map { idx ->
                executor.submit(Callable {
                    check(start.await(5, TimeUnit.SECONDS)) { "동시 실행 시작 신호 대기 시간이 초과되었습니다." }
                    try {
                        task(idx)
                    } finally {
                        done.countDown()
                    }
                })
            }
            start.countDown()
            assertThat(done.await(30, TimeUnit.SECONDS))
                .withFailMessage("동시 실행 작업이 제한 시간(30초) 내에 완료되지 않았습니다.")
                .isTrue()
            return futures.map {
                try {
                    it.get(3, TimeUnit.SECONDS)
                } catch (_: TimeoutException) {
                    throw IllegalStateException("작업 결과 수집 중 시간이 초과되었습니다.")
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    companion object {
        @Container
        @JvmStatic
        private val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.4")
            .withDatabaseName("wallet")
            .withUsername("wallet_app")
            .withPassword("wallet_pass")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.flyway.enabled") { true }
        }
    }
}
