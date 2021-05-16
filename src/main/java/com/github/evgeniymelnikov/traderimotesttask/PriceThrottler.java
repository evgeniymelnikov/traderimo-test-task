package com.github.evgeniymelnikov.traderimotesttask;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// Implementation is based on Plain Java.
// Subscribers will receive onPrice events which will come in producer (upstream) only after subscribing.
// So there is not any snapshot logic.
// Slow subscribers consume only most recent event for particular currency pair.
//
// New currencies rate will replace old without changing index. Assume we have USDTRX, USDBTC, USDETH rate ticks
// and after that 2 times USDBTC rate ticks. USDBTC last saved for consumer rate will be updated,
// but ordering will not changed: it will stay [USDTRX, USDBTC, USDETH], not [USDTRX, USDETH, USDBTC].
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
            removed.cancel();
        }
    }

    private static class Subscription {
        private final PriceProcessor priceProcessor;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        // use ArrayList, because it keeps ordering
        private final List<CurrencyPairRate> ccyRateList = new ArrayList<>();

        public Subscription(PriceProcessor priceProcessor) {
            this.priceProcessor = priceProcessor;
            Runnable consuming = () -> {
                CurrencyPairRate ccyRate = poolFirstCurrencyPairRate();
                if (ccyRate != null) {
                    priceProcessor.onPrice(ccyRate.ccy, ccyRate.rate);
                    System.out.printf("%s handle event %s%n", priceProcessor, ccyRate);
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


        // new currencies rate will replace old without changing index
        public void upsert(CurrencyPairRate rate) {
            synchronized (ccyRateList) {
                ListIterator<CurrencyPairRate> listIterator = ccyRateList.listIterator();
                while (listIterator.hasNext()) {
                    if (listIterator.next().equals(rate)) {
                        listIterator.set(rate);
                        return;
                    }
                }
                ccyRateList.add(rate);
            }
        }

        public void cancel() {
            System.out.printf("%s try to unsubscribe%n", priceProcessor);
            synchronized (ccyRateList) {
                ccyRateList.clear();
            }
            // period for gracefully shutdown
            try {
                executor.awaitTermination(1, TimeUnit.SECONDS);
                System.out.printf("%s successfully unsubscribed%n", priceProcessor);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private CurrencyPairRate poolFirstCurrencyPairRate() {
            synchronized (ccyRateList) {
                Iterator<CurrencyPairRate> iterator = ccyRateList.iterator();
                if (iterator.hasNext()) {
                    CurrencyPairRate currencyPairRate = iterator.next();
                    iterator.remove();
                    return currencyPairRate;
                } else {
                    return null;
                }
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
