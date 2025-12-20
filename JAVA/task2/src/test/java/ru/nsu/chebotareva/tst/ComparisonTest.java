package ru.nsu.chebotareva.tst;

import org.junit.jupiter.api.Test;
import ru.nsu.chebotareva.list.LinkedStringList;
import ru.nsu.chebotareva.sort.ArrayListBubbleSorter;
import ru.nsu.chebotareva.sort.LinkedListBubbleSorter;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ComparisonTest {

    @Test
    void printComparisonLinkedVsArray() throws Exception {
        int n = 2000;
        int threads = 4;
        long insideMs = 0;
        long betweenMs = 0;
        long windowMs = 3000;
        int maxLen = 80;

        double linked = measureLinked(n, threads, insideMs, betweenMs, maxLen, windowMs);
        double array = measureArray(n, threads, insideMs, betweenMs, maxLen, windowMs);

        double winner = Math.max(linked, array);
        double loser = Math.min(linked, array);
        double ratio = winner / Math.max(1e-9, loser);
        String winnerName = linked >= array ? "linked" : "array";

        System.out.printf(Locale.US,
                "COMPARISON: linked=%.1f/s array=%.1f/s winner=%s ratio=%.2f%n",
                linked, array, winnerName, ratio);

        printBar("linked", linked, winner);
        printBar("array ", array, winner);
    }

    @Test
    void printComparisonArrayFavoredScenario() throws Exception {
        int n = 900;
        int threads = 20;
        long insideMs = 12;
        long betweenMs = 0;
        long windowMs = 7000;
        int maxLen = 80;

        double linked = measureLinked(n, threads, insideMs, betweenMs, maxLen, windowMs);
        double array = measureArray(n, threads, insideMs, betweenMs, maxLen, windowMs);

        double winner = Math.max(linked, array);
        double loser = Math.min(linked, array);
        double ratio = winner / Math.max(1e-9, loser);
        String winnerName = linked >= array ? "linked" : "array";

        System.out.printf(Locale.US,
                "COMPARISON: linked=%.1f/s array=%.1f/s winner=%s ratio=%.2f%n",
                linked, array, winnerName, ratio);

        printBar("linked", linked, winner);
        printBar("array ", array, winner);
    }

    private static void printBar(String name, double value, double max) {
        int width = 40;
        int filled = (int) Math.round(value / Math.max(1e-9, max) * width);
        StringBuilder sb = new StringBuilder(width);
        for (int i = 0; i < filled; i++) sb.append('#');
        for (int i = filled; i < width; i++) sb.append('.');
        System.out.printf(Locale.US, "%s |%s| %.1f/s%n", name, sb, value);
    }

    private static double measureLinked(int n, int threads, long inside, long between,
                                        int maxLen, long windowMs) throws Exception {
        LinkedStringList list = new LinkedStringList();
        fillListLinked(list, n, maxLen);
        AtomicLong steps = new AtomicLong();
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            LinkedListBubbleSorter r = new LinkedListBubbleSorter(list, between, inside, steps);
            Thread t = new Thread(r, "cmp-linked-" + i);
            t.start();
            workers.add(t);
        }
        TimeUnit.MILLISECONDS.sleep(windowMs);
        for (Thread t : workers) t.interrupt();
        for (Thread t : workers) t.join(2000);
        return steps.get() * (1000.0 / windowMs);
    }

    private static double measureArray(int n, int threads, long inside, long between,
                                       int maxLen, long windowMs) throws Exception {
        List<String> backing = new ArrayList<>();
        List<String> list = Collections.synchronizedList(backing);
        fillListArray(list, n, maxLen);
        AtomicLong steps = new AtomicLong();
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            ArrayListBubbleSorter r = new ArrayListBubbleSorter(list, between, inside, steps);
            Thread t = new Thread(r, "cmp-array-" + i);
            t.start();
            workers.add(t);
        }
        TimeUnit.MILLISECONDS.sleep(windowMs);
        for (Thread t : workers) t.interrupt();
        for (Thread t : workers) t.join(2000);
        return steps.get() * (1000.0 / windowMs);
    }

    private static void fillListLinked(LinkedStringList list, int n, int maxLen) {
        Random r = new Random(42);
        for (int i = 0; i < n; i++) {
            String s = randomLetters(r, 10 + r.nextInt(30));
            for (String c : chunk(s, maxLen)) list.addFirst(c);
        }
    }

    private static void fillListArray(List<String> list, int n, int maxLen) {
        Random r = new Random(42);
        synchronized (list) {
            for (int i = 0; i < n; i++) {
                String s = randomLetters(r, 10 + r.nextInt(30));
                for (String c : chunk(s, maxLen)) list.add(0, c);
            }
        }
    }

    private static String randomLetters(Random r, int len) {
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
