package com.walletservice.wallet

import com.walletservice.wallet.api.dto.WithdrawRequest
import com.walletservice.wallet.domain.entity.WalletEntity
import com.walletservice.wallet.domain.entity.WalletTransactionStatus
import com.walletservice.wallet.domain.repository.WalletRepository
import com.walletservice.wallet.domain.repository.WalletTransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class WalletApiIntegrationTests {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

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
    fun `API 동시 출금 요청 시 잔액은 음수가 되지 않고 총 출금액은 초기 잔액을 초과하지 않는다`() {
        val initialBalance = BigDecimal("500000.0000")
        val withdrawAmount = 10000L
        val wallet = walletRepository.save(
            WalletEntity(
                publicWalletId = "wlt_01ARZ3NDEKTSV4RRFFQ69G5FB8",
                balance = initialBalance,
            ),
        )

        val responses = runConcurrently(taskCount = 100) { idx ->
            val request = WithdrawRequest(
                transactionId = "txn-api-concurrency-$idx",
                amount = withdrawAmount,
            )
            restTemplate.postForEntity(
                "/api/v1/wallets/${wallet.publicWalletId}/withdrawals",
                request,
                String::class.java,
            )
        }

        assertThat(responses).hasSize(100)
        val successResponses = responses.filter { it.statusCode == HttpStatus.OK }
        val insufficientResponses = responses.filter { it.statusCode == HttpStatus.CONFLICT }
        assertThat(successResponses).isNotEmpty
        assertThat(insufficientResponses).isNotEmpty
        assertThat(responses.map { it.statusCode }.distinct())
            .containsOnly(HttpStatus.OK, HttpStatus.CONFLICT)
        assertThat(insufficientResponses).allSatisfy { response ->
            assertThat(response.body).contains("\"code\":\"잔액_부족\"")
        }

        val updatedWallet = walletRepository.findById(wallet.id!!).orElseThrow()
        val transactions = walletTransactionRepository.findAll()
            .filter { it.walletId == wallet.id && it.transactionId.startsWith("txn-api-concurrency-") }

        val totalSuccessAmount = transactions
            .filter { it.status == WalletTransactionStatus.SUCCESS }
            .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }

        assertThat(transactions).hasSize(100)
        assertThat(updatedWallet.balance).isGreaterThanOrEqualTo(BigDecimal.ZERO)
        assertThat(totalSuccessAmount).isLessThanOrEqualTo(initialBalance)
        assertThat(updatedWallet.balance).isEqualByComparingTo(initialBalance.subtract(totalSuccessAmount))
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
                    it.get(5, TimeUnit.SECONDS)
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
