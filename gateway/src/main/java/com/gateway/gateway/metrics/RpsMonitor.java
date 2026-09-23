package com.gateway.gateway.metrics;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RpsMonitor {

    private final AtomicInteger currentSecondCount = new AtomicInteger(0);
    private final LinkedList<Integer> history = new LinkedList<>();
    private static final int MAX_HISTORY_SECONDS = 60;

    public void increment() {
        currentSecondCount.incrementAndGet();
    }

    @Scheduled(fixedRate = 1000)
    public void snapshotAndReset() {
        int count = currentSecondCount.getAndSet(0);
        synchronized (history) {
            history.addLast(count);
            if (history.size() > MAX_HISTORY_SECONDS) {
                history.removeFirst();
            }
        }
    }

    public List<Integer> getHistory() {
        synchronized (history) {
            return new LinkedList<>(history);
        }
    }
}
