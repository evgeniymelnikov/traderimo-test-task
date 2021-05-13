package com.github.evgeniymelnikov.traderimotesttask;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.*;

// todo: add some logging
public class PriceThrottler implements PriceProcessor {

    private final ConcurrentHashMap<PriceProcessor, Subscription> priceProcessorSubscriptionHolder = new ConcurrentHashMap<>();

    @Override
    public void onPrice(@NotNull String ccyPair, double rate) {
        CurrencyPairRate pairRate = new CurrencyPairRate(ccyPair, rate);
        priceProcessorSubscriptionHolder.values().forEach(subscription -> subscription.upsert(pairRate));
    }

    @Override
    public void subscribe(@NotNull PriceProcessor priceProcessor) {
        priceProcessorSubscriptionHolder.computeIfAbsent(
                priceProcessor,
                Subscription::new
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
        private final SetBlockingQueue<CurrencyPairRate> tasks = new SetBlockingQueue<>();

        public Subscription(PriceProcessor priceProcessor) {
            this.priceProcessor = priceProcessor;
            Runnable consuming = () -> {
                CurrencyPairRate currencyPairRate;
                try {
                    currencyPairRate = tasks.take();
                    priceProcessor.onPrice(currencyPairRate.ccy, currencyPairRate.rate);
                } catch (InterruptedException e) {
                    e.printStackTrace();
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
            tasks.add(rate);
        }

        public void unsubscribe() {
            // period for gracefully shutdown
            try {
                executor.awaitTermination(1, TimeUnit.SECONDS);
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

    // !!! for correct behaviour you must use only overridden methods !!!
    // not thread safety for consuming (only one thread can safety consume elements via take method or poll)
    private static class SetBlockingQueue<T> extends LinkedBlockingQueue<T> {
        private final Set<T> set = new LinkedHashSet<>();

        @Override
        public synchronized boolean add(T t) {
            if (set.contains(t)) {
                return false;
            } else {
                set.add(t);
                return super.add(t);
            }
        }

        @Override
        public T poll(long timeout, TimeUnit unit) throws InterruptedException {
            T t = super.poll(timeout, unit);
            if (t != null) {
                set.remove(t);
            }
            return t;
        }

        @Override
        public T take() throws InterruptedException {
            T t = super.take();
            set.remove(t);
            return t;
        }
    }
}
