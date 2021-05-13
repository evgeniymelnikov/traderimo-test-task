package com.github.evgeniymelnikov.traderimotesttask

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class CoroutinePriceThrottler : PriceProcessor {

    private val flowHolder = ConcurrentHashMap<String, MutableStateFlow<CurrencyPairRate>>()
    private val subscribersHolder = ConcurrentHashMap<PriceProcessor, Set<MutableStateFlow<CurrencyPairRate>>>()

    override fun onPrice(ccyPair: String, rate: Double) {
        flowHolder.computeIfAbsent(ccyPair) {
            MutableStateFlow(CurrencyPairRate(ccyPair, rate)).also { println("New $ccyPair") }
        }.value = CurrencyPairRate(ccyPair, rate).also { println("Price changed for $ccyPair") }
    }

    override fun subscribe(priceProcessor: PriceProcessor) {
        subscribersHolder.computeIfAbsent(priceProcessor) {
            flowHolder.values.toSet()
        }.forEach {
            // todo: migrate from GlobalScope to custom scope.
            GlobalScope.launch {
                it.collect { currencyPairRate ->
                    priceProcessor.onPrice(
                        currencyPairRate.ccyPair,
                        currencyPairRate.rate
                    ).also { println("Handle event ${currencyPairRate.ccyPair}") }
                }
            }
        }.also { println("Got new subscription") }
    }

    override fun unsubscribe(priceProcessor: PriceProcessor) {
        TODO("Not yet implemented")
    }

    private data class CurrencyPairRate(
        val ccyPair: String,
        val rate: Double
    )
}