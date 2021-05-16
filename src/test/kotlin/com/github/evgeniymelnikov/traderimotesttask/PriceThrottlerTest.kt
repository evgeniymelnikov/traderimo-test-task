package com.github.evgeniymelnikov.traderimotesttask

class PriceThrottlerTest : BasePriceThrottlerTest<PriceThrottler>() {

    override fun priceThrottlerProvider(): PriceThrottler {
        return PriceThrottler()
    }
}