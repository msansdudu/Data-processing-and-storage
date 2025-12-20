package ru.nsu.chebotareva.tst;

import org.junit.jupiter.api.Test;
import ru.nsu.chebotareva.sort.ArrayListBubbleSorter;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class ArrayListModeTest {

    @Test
    void sortsWithDelaysAndMultipleThreads() throws Exception {
        List<String> base = new ArrayList<>();
        List<String> list = Collections.synchronizedList(base);
        synchronized (list) {
            for (String s : Arrays.asList("delta", "alpha", "charlie", "bravo")) {
                list.add(0, s);
            }
        }

        int threads = 3;
        long insideMs = 10;
        long betweenMs = 10;
        AtomicLong steps = new AtomicLong();
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            ArrayListBubbleSorter r = new ArrayListBubbleSorter(list, betweenMs, insideMs, steps);
            Thread t = new Thread(r, "spec-array-" + i);
            t.start();
            workers.add(t);
        }

        TimeUnit.MILLISECONDS.sleep(600);
        for (Thread t : workers) t.interrupt();
        for (Thread t : workers) t.join(2000);

        List<String> snap;
        synchronized (list) { snap = new ArrayList<>(list); }

        assertTrue(steps.get() > 0, "There should be at least some sort steps");
        assertEquals(4, snap.size());
        assertEquals(Arrays.asList("alpha", "bravo", "charlie", "delta"), snap);
    }

    @Test
    void chunkingSplitsLongStringsAt80() {
        String s = randomLetters(205);
        int maxLen = 80;
        List<String> chunks = chunk(s, maxLen);
        assertEquals(3, chunks.size());
        assertEquals(80, chunks.get(0).length());
        assertEquals(80, chunks.get(1).length());
        assertEquals(45, chunks.get(2).length());
        assertEquals(s, String.join("", chunks));

        List<String> base = new ArrayList<>();
        List<String> sync = Collections.synchronizedList(base);
        synchronized (sync) {
            for (String c : chunks) sync.add(0, c);
        }
        List<String> snap;
        synchronized (sync) { snap = new ArrayList<>(sync); }

        Collections.reverse(chunks);
        assertEquals(chunks, snap);
    }

    private static String randomLetters(int len) {
        Random r = new Random(42);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append((char) ('a' + r.nextInt(26)));
        return sb.toString();
    }

    private static List<String> chunk(String s, int maxLen) {
        List<String> res = new ArrayList<>();
        for (int i = 0; i < s.length(); i += maxLen) {
            res.add(s.substring(i, Math.min(i + maxLen, s.length())));
        }
        return res;
    }
}
