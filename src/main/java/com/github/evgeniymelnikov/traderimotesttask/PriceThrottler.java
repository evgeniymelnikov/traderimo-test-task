package com.github.evgeniymelnikov.traderimotesttask;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// Implementation is based on Plain Java.
// Subscribers will receive onPrice events which will come in producer (upstream) only after subscribing.
// So there is not any snapshot logic.
// Slow subscribers consume only most recent event for particular currency pair.
public class PriceThrottler implements PriceProcessor {

    private final ConcurrentHashMap<PriceProcessor, Subscription> priceProcessorSubscriptionHolder = new ConcurrentHashMap<>();

    @Override
    public void onPrice(@NotNull String ccyPair, double rate) {
        CurrencyPairRate pairRate = new CurrencyPairRate(ccyPair, rate);
        priceProcessorSubscriptionHolder.values().forEach(subscription -> subscription.upsert(pairRate));
        System.out.printf("Got event %s%n", pairRate);
    }

    @Override
    public void subscribe(@NotNull PriceProcessor priceProcessor) {
        priceProcessorSubscriptionHolder.computeIfAbsent(
                priceProcessor,
                (pp) -> {
                    Subscription subscription = new Subscription(pp);
                    System.out.printf("Got new subscription by %s%n", pp);
                    return subscription;
                }
        );
    }

    @Override
    public void unsubscribe(@NotNull PriceProcessor priceProcessor) {
        Subscription removed = priceProcessorSubscriptionHolder.remove(priceProcessor);
        if (removed != null) {
            removed.unsubscribe();
        }
    }

    private static class Subscription {
        private final PriceProcessor priceProcessor;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final ConcurrentSkipListMap<String, Double> tasks = new ConcurrentSkipListMap<>();

        public Subscription(PriceProcessor priceProcessor) {
            this.priceProcessor = priceProcessor;
            Runnable consuming = () -> {
                Map.Entry<String, Double> ccyRate = tasks.pollFirstEntry();
                if (ccyRate != null) {
                    String ccy = ccyRate.getKey();
                    Double rate = ccyRate.getValue();
                    priceProcessor.onPrice(ccy, rate);
                    System.out.printf("%s handle event %s %s%n", priceProcessor, ccy, rate);
                }
            };
            initRecursiveTaskCreation(consuming, executor);
        }

        private void initRecursiveTaskCreation(Runnable consuming, Executor executor) {
            executor.execute(() -> {
                consuming.run();
                initRecursiveTaskCreation(consuming, executor);
            });
        }


        public void upsert(CurrencyPairRate rate) {
            tasks.put(rate.ccy, rate.rate);
        }

        public void unsubscribe() {
            System.out.printf("%s try to unsubscribe%n", priceProcessor);
            // period for gracefully shutdown
            try {
                executor.awaitTermination(1, TimeUnit.SECONDS);
                System.out.printf("%s successfully unsubscribed%n", priceProcessor);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static class CurrencyPairRate {
        private final String ccy;
        private final Double rate;

        public CurrencyPairRate(String ccy, Double rate) {
            this.ccy = ccy;
            this.rate = rate;
        }

        @Override
        public String toString() {
            return "CurrencyPairRate{" +
                    "ccy='" + ccy + '\'' +
                    ", rate=" + rate +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CurrencyPairRate that = (CurrencyPairRate) o;

            return ccy.equals(that.ccy);
        }

        @Override
        public int hashCode() {
            return ccy.hashCode();
        }
    }
}
