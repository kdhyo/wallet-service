package com.walletservice.wallet.api

import com.walletservice.wallet.api.dto.GetWalletTransactionsRequest
import com.walletservice.wallet.api.dto.GetWalletTransactionsResponse
import com.walletservice.wallet.api.dto.PageInfoResponse
import com.walletservice.wallet.api.dto.WithdrawRequest
import com.walletservice.wallet.api.dto.WithdrawResponse
import com.walletservice.wallet.common.GlobalExceptionHandler
import com.walletservice.wallet.domain.service.WalletTransactionQueryService
import com.walletservice.wallet.domain.service.WalletWithdrawalService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [WalletWithdrawalController::class, WalletTransactionQueryController::class])
@Import(GlobalExceptionHandler::class)
class ControllerValidationTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var walletWithdrawalService: WalletWithdrawalService

    @MockitoBean
    private lateinit var walletTransactionQueryService: WalletTransactionQueryService

    @Test
    fun `출금 요청 amount가 0이면 400을 반환한다`() {
        val requestBody = """
            {
              "transaction_id": "txn-1",
              "amount": 0
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/wallets/wlt_01ARZ3NDEKTSV4RRFFQ69G5FAV/withdrawals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("잘못된_요청"))
    }

    @Test
    fun `출금 요청 amount가 양수이면 200을 반환한다`() {
        val requestBody = """
            {
              "transaction_id": "txn-1",
              "amount": 1
            }
        """.trimIndent()

        given(
            walletWithdrawalService.withdraw(
                "wlt_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                WithdrawRequest(transactionId = "txn-1", amount = 1),
            ),
        )
            .willReturn(
                WithdrawResponse(
                    transactionId = "txn-1",
                    walletId = "wlt_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                    amount = 1,
                    balanceAfter = 9999,
                    status = "SUCCESS",
                    errorCode = null,
                    processedAt = null,
                ),
            )

        mockMvc.perform(
            post("/api/v1/wallets/wlt_01ARZ3NDEKTSV4RRFFQ69G5FAV/withdrawals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `거래내역 조회 limit가 0이면 400을 반환한다`() {
        mockMvc.perform(
            get("/api/v1/wallets/wlt_01ARZ3NDEKTSV4RRFFQ69G5FAV/transactions")
                .queryParam("limit", "0"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("잘못된_요청"))
    }

    @Test
    fun `정상 조회 요청은 200을 반환한다`() {
        given(
            walletTransactionQueryService.getTransactions(
                GetWalletTransactionsRequest(
                    walletId = "wlt_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                    limit = 20,
                    cursor = null,
                ),
            ),
        )
            .willReturn(
                GetWalletTransactionsResponse(
                    walletId = "wlt_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                    transactions = emptyList(),
                    pageInfo = PageInfoResponse(
                        limit = 20,
                        hasNext = false,
                        nextCursor = null,
                    ),
                ),
            )

        mockMvc.perform(
            get("/api/v1/wallets/wlt_01ARZ3NDEKTSV4RRFFQ69G5FAV/transactions")
                .queryParam("limit", "20"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.wallet_id").value("wlt_01ARZ3NDEKTSV4RRFFQ69G5FAV"))
    }
}
