package com.github.evgeniymelnikov.traderimotesttask

import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PriceThrottlerTest {

    @Test
    fun `simple test`() {
        val priceThrottler = CoroutinePriceThrottler()
        val finalProcessor1 = FinalProcessor("FP1")
        val finalProcessor2 = FinalProcessor("FP2")
        val executor = Executors.newScheduledThreadPool(4)

        val usdRub = "USDRUB"
        val usdXtz = "USDXTZ"
        val rates = generateSequence(10, usdRub) + generateSequence(3, usdXtz)
        executor.schedule({
            priceThrottler.subscribe(finalProcessor1)
        }, 2000, TimeUnit.MILLISECONDS)

        executor.schedule({
            // priceThrottler.unsubscribe(finalProcessor1)
            priceThrottler.subscribe(finalProcessor2)
        }, 4000, TimeUnit.MILLISECONDS)
        rates.forEach {
            if (it.first == usdRub) {
                Thread.sleep(300)
                executor.schedule({ priceThrottler.onPrice(it.first, it.second) }, 0, TimeUnit.MILLISECONDS)
            }
            if (it.first == usdXtz) {
                Thread.sleep(3000)
                executor.schedule({ priceThrottler.onPrice(it.first, it.second) }, 0, TimeUnit.SECONDS)
            }
        }

        Thread.sleep(50000)
    }

    fun generateSequence(count: Int, currencyPair: String): List<Pair<String, Double>> {
        return IntRange(1, count).map { Pair(currencyPair, it.toDouble()) }
    }
}