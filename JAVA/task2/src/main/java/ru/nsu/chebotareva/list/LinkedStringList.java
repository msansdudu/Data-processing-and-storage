package ru.nsu.chebotareva.list;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe doubly-linked list implementation for strings.
 * Uses fine-grained locking to allow concurrent access and modifications.
 */
public class LinkedStringList implements IterableStringList {
    private final ListNode sentinelHead;
    private final ListNode sentinelTail;
    private final AtomicInteger elementCount = new AtomicInteger(0);

    public LinkedStringList() {
        this.sentinelHead = new ListNode(null);
        this.sentinelTail = new ListNode(null);
        sentinelHead.next = sentinelTail;
        sentinelTail.prev = sentinelHead;
    }

    @Override
    public void addFirst(String value) {
        if (value == null) throw new IllegalArgumentException("Null values are not allowed");

        ListNode newNode = new ListNode(value);

        // Lock ordering: acquire sentinel head first, then first element
        sentinelHead.lock.lock();
        ListNode currentFirst;
        try {
            currentFirst = sentinelHead.next;
            if (currentFirst != sentinelTail) currentFirst.lock.lock();
            try {
                // Insert new node between sentinel head and current first
                newNode.prev = sentinelHead;
                newNode.next = currentFirst;
                sentinelHead.next = newNode;
                if (currentFirst != sentinelTail) currentFirst.prev = newNode;
                elementCount.incrementAndGet();
            } finally {
                if (currentFirst != sentinelTail) currentFirst.lock.unlock();
            }
        } finally {
            sentinelHead.lock.unlock();
        }
    }

    @Override
    public int size() {
        return elementCount.get();
    }

    @Override
    public Iterator<String> iterator() {
        return new Iterator<String>() {
            ListNode currentNode;
            {
                // Initialize at first real node with proper locking
                sentinelHead.lock.lock();
                try {
                    currentNode = sentinelHead.next;
                    if (currentNode == sentinelTail) currentNode = null;
                    if (currentNode != null) currentNode.lock.lock();
                } finally {
                    sentinelHead.lock.unlock();
                }
            }

            @Override
            public boolean hasNext() {
                return currentNode != null;
            }

            @Override
            public String next() {
                if (currentNode == null) throw new NoSuchElementException("No more elements in the list");

                String result = currentNode.value;

                // Lock coupling: acquire next node's lock before releasing current
                ListNode nextNode = currentNode.next;
                if (nextNode == sentinelTail) nextNode = null;
                if (nextNode != null) nextNode.lock.lock();
                currentNode.lock.unlock();
                currentNode = nextNode;

                return result;
            }
        };
    }

    @Override
    public List<String> snapshot() {
        List<String> copy = new ArrayList<>(Math.max(0, size()));
        Iterator<String> listIterator = iterator();
        while (listIterator.hasNext()) {
            copy.add(listIterator.next());
        }
        return copy;
    }

    /**
     * Performs one complete pass of bubble sort on the list.
     * @param swapDelayMs delay between swap operations in milliseconds
     * @param stepDelayMs delay between comparison steps in milliseconds
     * @param operationCallback callback to execute after each comparison
     * @return number of swaps performed
     * @throws InterruptedException if the thread is interrupted
     */
    public int performBubbleSortPass(long swapDelayMs, long stepDelayMs, Runnable operationCallback) throws InterruptedException {
        int swapCount = 0;
        ListNode current = sentinelHead;

        while (!Thread.currentThread().isInterrupted()) {
            ListNode nextCurrent = null; // Position for current in next iteration

            // Acquire lock on current node first (head-to-tail ordering)
            current.lock.lock();
            try {
                ListNode nodeA = current.next;
                if (nodeA == sentinelTail) {
                    return swapCount; // End of list reached
                }

                nodeA.lock.lock();
                try {
                    // Verify adjacency after acquiring locks
                    if (current.next != nodeA || nodeA.prev != current) {
                        nextCurrent = current; // Retry with same position
                    } else {
                        ListNode nodeB = nodeA.next;
                        if (nodeB == sentinelTail) {
                            return swapCount; // Only one element left
                        }

                        nodeB.lock.lock();
                        try {
                            // Verify adjacency of A and B
                            if (nodeA.next != nodeB || nodeB.prev != nodeA) {
                                nextCurrent = current; // Retry
                            } else {
                                // Perform delay before comparison if specified
                                if (swapDelayMs > 0) {
                                    Thread.sleep(swapDelayMs);
                                }

                                // Execute callback for operation counting
                                if (operationCallback != null) operationCallback.run();

                                // Compare and swap if needed
                                if (nodeA.value.compareTo(nodeB.value) > 0) {
                                    // Perform swap by relinking: current <-> B <-> A <-> afterB
                                    ListNode afterB = nodeB.next; // Could be sentinel tail

                                    // Relink nodes for the swap
                                    current.next = nodeB;
                                    nodeB.prev = current;

                                    nodeB.next = nodeA;
                                    nodeA.prev = nodeB;

                                    nodeA.next = afterB;
                                    if (afterB != sentinelTail) afterB.prev = nodeA;

                                    swapCount++;
                                    nextCurrent = current.next; // B is now at A's position
                                } else {
                                    // No swap needed, advance to A
                                    nextCurrent = nodeA;
                                }
                            }
                        } finally {
                            nodeB.lock.unlock();
                        }
                    }
                } finally {
                    nodeA.lock.unlock();
                }
            } finally {
                current.lock.unlock();
            }

            // Delay between steps (outside of locks)
            if (stepDelayMs > 0) {
                Thread.sleep(stepDelayMs);
            }

            // Update current position for next iteration
            current = (nextCurrent != null) ? nextCurrent : sentinelHead;
        }
        return swapCount;
    }

    /**
     * Internal node class representing an element in the doubly-linked list.
     * Each node has its own lock for fine-grained concurrency control.
     */
    private static final class ListNode {
        final ReentrantLock lock = new ReentrantLock();
        String value;
        ListNode prev;
        ListNode next;

        ListNode(String value) {
            this.value = value;
        }
    }
}
