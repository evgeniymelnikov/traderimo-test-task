package com.github.evgeniymelnikov.traderimotesttask

class SnapshotPriceThrottlerTest : BasePriceThrottlerTest<SnapshotPriceThrottler>() {

    override fun priceThrottlerProvider(): SnapshotPriceThrottler {
        return SnapshotPriceThrottler()
    }
}