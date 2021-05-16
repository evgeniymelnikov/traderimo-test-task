package com.github.evgeniymelnikov.traderimotesttask

import com.github.evgeniymelnikov.traderimotesttask.BasePriceThrottlerTest.Checker
import java.time.Duration

class TestProcessor(
    private val name: String,
    private val processingTime: Duration = Duration.ofSeconds(1L),
    private val checker: Checker
) : PriceProcessor {

    override fun onPrice(ccyPair: String, rate: Double) {
        checker.check(ccyPair, rate, this)
        try {
            Thread.sleep(processingTime.toMillis())
        } catch (exc: Exception) {
            println("$this. Execution for $ccyPair $rate has been interrupted")
        }

    }

    override fun subscribe(priceProcessor: PriceProcessor) {
        TODO("Not yet implemented")
    }

    override fun unsubscribe(priceProcessor: PriceProcessor) {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return "TestProcessor(name='$name')"
    }
}