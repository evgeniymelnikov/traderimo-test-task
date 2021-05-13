package com.github.evgeniymelnikov.traderimotesttask

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

// Implementation is based on kotlin coroutines.
// Subscribers will receive onPrice last events for every currency pairs before subscribing (something like snapshot)
// and new events which will come in producer (upstream) only after subscribing.
// Slow subscribers consume only most recent event for particular currency pair.
class CoroutinePriceThrottler : PriceProcessor {

    private val flowHolder = ConcurrentHashMap<String, MutableStateFlow<CurrencyPairRate>>()
    private val subscribersHolder = ConcurrentHashMap<PriceProcessor, Set<CurrencyPairRateFlowSubscription>>()
    private val lock = ReentrantReadWriteLock()

    override fun onPrice(ccyPair: String, rate: Double) {
        val currencyPairRate = CurrencyPairRate(ccyPair, rate)
        var firstEvent = false
        val mutableStateFlow = flowHolder.computeIfAbsent(ccyPair) {
            firstEvent = true
            val flow = MutableStateFlow(currencyPairRate).also { println("Got first event $currencyPairRate with that currency") }
            lock.write {
                subscribersHolder.forEach {
                    if (it.value.any { flowSubscription -> flowSubscription.currency == ccyPair }) {
                        return@forEach
                    } else {
                        subscribeFlow(flow, it.key)
                    }
                }
            }
            flow
        }
        if (!firstEvent) {
            mutableStateFlow.value = currencyPairRate.also {
                println("Got event $currencyPairRate")
            }
        }
    }

    override fun subscribe(priceProcessor: PriceProcessor) {
        lock.read {
            subscribersHolder.computeIfAbsent(priceProcessor) {
                flowHolder.values.toSet().map {
                    // todo: migrate from GlobalScope to custom scope.
                    subscribeFlow(it, priceProcessor)
                }.toSet()
                    .also { println("Got new subscription by $priceProcessor") }
            }
        }
    }

    private fun subscribeFlow(
        it: MutableStateFlow<CurrencyPairRate>,
        priceProcessor: PriceProcessor
    ): CurrencyPairRateFlowSubscription {
        // todo: use custom scope
        return GlobalScope.launch {
            it.collect { currencyPairRate ->
                priceProcessor.onPrice(
                    currencyPairRate.ccyPair,
                    currencyPairRate.rate
                )
                    .also { println("$priceProcessor handle event $currencyPairRate") }
            }
        }.let { job -> CurrencyPairRateFlowSubscription(it.value.ccyPair, job) }
    }

    override fun unsubscribe(priceProcessor: PriceProcessor) {
        subscribersHolder.remove(priceProcessor)?.let {
            it.forEach { holder -> holder.job.cancel("Cancel subscription") }
        }
    }

    // every new rate should be processed. Corresponding to MutableStateFlow logic,
    // we should use reference equality (not override equals & hashcode) for avoid skipping events with identical fields
    // (as option: we can generate something like eventId when receive that events in onPrice method
    // and check equality by that field).
    private class CurrencyPairRate(
        val ccyPair: String,
        val rate: Double
    ) {
        override fun toString(): String {
            return "CurrencyPairRate(ccyPair='$ccyPair', rate=$rate)"
        }
    }

    private class CurrencyPairRateFlowSubscription(
        val currency: String,
        val job: Job
    )
}