package com.github.evgeniymelnikov.traderimotesttask

interface PriceProcessor {
    /**
     * Call from an upstream
     *
     * You can assume that only correct data comes in here - no need for extra validation
     *
     * @param ccyPair - EURUSD, EURRUB, USDJPY - up to 200 different currency pairs
     * @param rate - any double rate like 1.12, 200.23 etc
     */
    fun onPrice(ccyPair: String, rate: Double)

    /**
     * Subscribe for updates
     *
     * Called rarely during operation of PriceProcessor
     *
     * @param priceProcessor - can be up to 200 subscribers
     */
    fun subscribe(priceProcessor: PriceProcessor)

    /**
     * Unsubscribe from updates
     *
     * Called rarely during operation of PriceProcessor
     *
     * @param priceProcessor
     */
    fun unsubscribe(priceProcessor: PriceProcessor)
}