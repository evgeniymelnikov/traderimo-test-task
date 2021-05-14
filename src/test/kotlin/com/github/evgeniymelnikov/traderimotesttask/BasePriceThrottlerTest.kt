package com.github.evgeniymelnikov.traderimotesttask

import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.absoluteValue
import kotlin.test.assertTrue

// Don't know how check with multiple producers (multithreading),
// because check flow (register current value then send in onPrice) cannot be atomicity without synchronizing
// (or another monitor blocking), which will make multithreading in fact single threading.
// So has used allowable indent for this case.
//
// For example: first thread inserted into checker rate 1.0 also send it to onPrice, second thread - rate 2.0 and send it to onPrice,
// because these have done in race condition, behaviour unpredictable.
abstract class BasePriceThrottlerTest<T : PriceProcessor> {

    abstract fun priceThrottlerProvider(): T

    @Test
    fun `fast single-thread producer and 1 subscriber with fast consuming`() {
        // arrange
        val upstream = priceThrottlerProvider()
        val checker = CheckerForSingleThreadProducer()
        val testProcessor = TestProcessor("1", Duration.ofMillis(100), checker)
        upstream.subscribe(testProcessor)
        val delay = Duration.ofMillis(100)

        // act & assert (all assertions are inside TestProcessor)
        val generateStream = generateStream("USDXTZ", delay, 1, upstream, checker)

        Thread.sleep(3000)
        generateStream.stop()
        checker.result()
    }

    @Test
    fun `fast single-thread producer and 1 subscriber with fast consuming and 1 with low`() {
        // arrange
        val upstream = priceThrottlerProvider()
        val checker = CheckerForSingleThreadProducer()
        val testProcessor = TestProcessor("1", Duration.ofMillis(100), checker)
        val testProcessor2 = TestProcessor("2", Duration.ofMillis(1500), checker)
        upstream.subscribe(testProcessor)
        upstream.subscribe(testProcessor2)

        val delay = Duration.ofMillis(100)

        // act & assert (all assertions are inside TestProcessor)
        val generateStream = generateStream("USDTRX", delay, 1, upstream, checker)

        Thread.sleep(6000)
        generateStream.stop()
        checker.result()
    }

    // MULTITHREADING. See comment at the head of the class. If it was fail, you could ignore it.
    @Test
    fun `fast multi-thread producer and 1 subscriber with fast consuming`() {
        // arrange
        val upstream = priceThrottlerProvider()
        val checker = CheckerForMultithreadingProducer(3.0)
        val testProcessor = TestProcessor("1", Duration.ofMillis(100), checker)
        upstream.subscribe(testProcessor)

        val delay = Duration.ofMillis(100)

        // act & assert (all assertions are inside TestProcessor)
        val generateStream = generateStream("USDTRX", delay, 3, upstream, checker)

        Thread.sleep(3000)
        generateStream.stop()
        checker.result()
    }

    @Test
    fun `fast single thread producer and 1 consumer which unsubscribed and then subscribed again`() {
        // arrange
        val upstream = priceThrottlerProvider()
        val checker = CheckerForSingleThreadProducer()
        val testProcessor = TestProcessor("1", Duration.ofMillis(200), checker)
        upstream.subscribe(testProcessor)
        val delay = Duration.ofMillis(100)

        // act & assert (all assertions are inside TestProcessor)
        val generateStream = generateStream("USDBTC", delay, 1, upstream, checker)
        Thread.sleep(1000)

        upstream.unsubscribe(testProcessor)
        Thread.sleep(1000)

        upstream.subscribe(testProcessor)
        Thread.sleep(3000)

        generateStream.stop()
        checker.result()
    }

    @Test
    fun `2 single thread producers and 2 consumers which unsubscribed and then subscribed again`() {
        // arrange
        val upstream = priceThrottlerProvider()
        val checker = CheckerForMultithreadingProducer(3.0)
        val testProcessor = TestProcessor("1", Duration.ofMillis(200), checker)
        val testProcessor2 = TestProcessor("2", Duration.ofMillis(300), checker)
        upstream.subscribe(testProcessor)
        upstream.subscribe(testProcessor2)
        val delay = Duration.ofMillis(300)

        // act & assert (all assertions are inside TestProcessor)
        val usdBtcStream = generateStream("USDBTC", delay, 1, upstream, checker)
        Thread.sleep(100)
        val usdEthStream = generateStream("USDETH", delay, 1, upstream, checker)
        Thread.sleep(1000)

        upstream.unsubscribe(testProcessor)
        upstream.unsubscribe(testProcessor2)
        Thread.sleep(1000)

        upstream.subscribe(testProcessor)
        upstream.subscribe(testProcessor2)
        Thread.sleep(3000)

        usdBtcStream.stop()
        usdEthStream.stop()
        checker.result()
    }

    private fun generateStream(
        currencyPair: String,
        delay: Duration,
        threadCounts: Int,
        upstream: PriceProcessor,
        checker: Checker
    ): Generator {
        val rateGenerator = AtomicLong()
        return IntRange(1, threadCounts).map {
            val timer = Timer()
            timer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    val rate = rateGenerator.incrementAndGet().toDouble()
                    checker.registerCurrent(currencyPair, rate)
                    upstream.onPrice(currencyPair, rate)
                }
            }, 0, delay.toMillis())
            timer
        }.let { Generator(it) }
    }

    interface Checker {
        fun registerCurrent(currency: String, rate: Double)
        fun check(currencyPair: String, rate: Double, priceProcessor: PriceProcessor)
        fun result()
    }

    inner class CheckerForSingleThreadProducer : Checker {
        private val currencyPairRateMap = ConcurrentHashMap<String, Double>()

        private val errors = ConcurrentLinkedQueue<String>()

        override fun registerCurrent(currency: String, rate: Double) {
            currencyPairRateMap.put(currency, rate)
        }

        override fun check(currencyPair: String, rate: Double, priceProcessor: PriceProcessor) {
            val expected = currencyPairRateMap[currencyPair]
            if (expected != rate) {
                errors.add("$priceProcessor got $currencyPair $rate, but last registered is $expected")
            }
        }

        override fun result() {
            assertTrue(errors.isEmpty(), errors.toList().toString())
        }
    }

    inner class CheckerForMultithreadingProducer(
        private val allowableIndent: Double
    ) : Checker {
        private val currencyPairRateMap = ConcurrentHashMap<String, Double>()

        private val errors = ConcurrentLinkedQueue<String>()

        override fun registerCurrent(currency: String, rate: Double) {
            currencyPairRateMap.put(currency, rate)
        }

        override fun check(currencyPair: String, rate: Double, priceProcessor: PriceProcessor) {
            val expected = currencyPairRateMap[currencyPair]
            if ((rate - expected!!).absoluteValue >= allowableIndent) {
                errors.add("$priceProcessor got $currencyPair $rate, but last registered is $expected")
            }
        }

        override fun result() {
            assertTrue(errors.isEmpty(), errors.toList().toString())
        }
    }

    inner class Generator(
        private val timers: List<Timer>
    ) {
        fun stop() {
            timers.forEach { it.cancel() }
        }
    }
}