package ru.nsu.chebotareva.sort;

import ru.nsu.chebotareva.list.LinkedStringList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bubble sort worker for LinkedStringList.
 * Continuously performs bubble sort passes on the shared list.
 */
public final class LinkedListBubbleSorter implements Runnable {
    private final LinkedStringList targetList;
    private final long stepDelayMs;
    private final long comparisonDelayMs;
    private final AtomicLong operationCounter;
    private volatile boolean isActive = true;

    public LinkedListBubbleSorter(LinkedStringList targetList, long stepDelayMs, long comparisonDelayMs, AtomicLong operationCounter) {
        this.targetList = targetList;
        this.stepDelayMs = stepDelayMs;
        this.comparisonDelayMs = comparisonDelayMs;
        this.operationCounter = operationCounter;
    }

    /**
     * Signals the sorter to stop execution.
     */
    public void shutdown() {
        isActive = false;
    }

    @Override
    public void run() {
        try {
            while (isActive && !Thread.currentThread().isInterrupted()) {
                targetList.performBubbleSortPass(comparisonDelayMs, stepDelayMs, operationCounter::incrementAndGet);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}