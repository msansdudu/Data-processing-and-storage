package ru.nsu.chebotareva.sort;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bubble sort worker for synchronized ArrayList.
 * Continuously performs bubble sort passes on the shared synchronized list.
 */
public final class ArrayListBubbleSorter implements Runnable {
    private final List<String> targetList;
    private final long stepDelayMs;
    private final long comparisonDelayMs;
    private final AtomicLong operationCounter;
    private volatile boolean isActive = true;

    public ArrayListBubbleSorter(List<String> synchronizedList,
                                 long stepDelayMs,
                                 long comparisonDelayMs,
                                 AtomicLong operationCounter) {
        this.targetList = synchronizedList;
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
                int listSize;
                synchronized (targetList) { listSize = targetList.size(); }

                for (int position = 0; position < Math.max(0, listSize - 1) && isActive && !Thread.currentThread().isInterrupted(); position++) {
                    // Delay before comparison if specified
                    if (comparisonDelayMs > 0) {
                        TimeUnit.MILLISECONDS.sleep(comparisonDelayMs);
                    }

                    synchronized (targetList) {
                        if (position + 1 >= targetList.size()) break;

                        String firstElement = targetList.get(position);
                        String secondElement = targetList.get(position + 1);

                        if (firstElement.compareTo(secondElement) > 0) {
                            Collections.swap(targetList, position, position + 1);
                        }
                    }

                    operationCounter.incrementAndGet();

                    // Delay between steps if specified
                    if (stepDelayMs > 0) {
                        TimeUnit.MILLISECONDS.sleep(stepDelayMs);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
