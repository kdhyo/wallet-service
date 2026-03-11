package com.walletservice.wallet.domain.util

import java.math.BigDecimal
import java.math.RoundingMode

fun BigDecimal.toClientLong(): Long {
    return setScale(0, RoundingMode.DOWN).longValueExact()
}
