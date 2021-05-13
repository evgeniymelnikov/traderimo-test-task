package com.github.evgeniymelnikov.traderimotesttask

class FinalProcessor(
    private val name: String
) : PriceProcessor {

    override fun onPrice(ccyPair: String, rate: Double) {
        println("FinalProcessor $name got currency pair rate: $ccyPair, $rate")
        Thread.sleep(1000)
    }

    override fun subscribe(priceProcessor: PriceProcessor) {
        TODO("Not yet implemented")
    }

    override fun unsubscribe(priceProcessor: PriceProcessor) {
        TODO("Not yet implemented")
    }
}