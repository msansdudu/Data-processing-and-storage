package ru.nsu.chebotareva.app;

import ru.nsu.chebotareva.list.LinkedStringList;
import ru.nsu.chebotareva.sort.ArrayListBubbleSorter;
import ru.nsu.chebotareva.sort.LinkedListBubbleSorter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Main {
    private static class ApplicationConfig {
        String listType = "custom_linked";
        int workerCount = 2;
        long stepDelayMs = 100;
        long swapDelayMs = 100;
        int maxStringLength = 80;
    }

    private static ApplicationConfig parseCommandLineArgs(String[] args) {
        ApplicationConfig config = new ApplicationConfig();
        for (String arg : args) {
            if (arg.startsWith("--mode=")) config.listType = arg.substring("--mode=".length());
            else if (arg.startsWith("--threads=")) config.workerCount = Integer.parseInt(arg.substring("--threads=".length()));
            else if (arg.startsWith("--delayBetween=")) config.stepDelayMs = Long.parseLong(arg.substring("--delayBetween=".length()));
            else if (arg.startsWith("--delayInside=")) config.swapDelayMs = Long.parseLong(arg.substring("--delayInside=".length()));
            else if (arg.startsWith("--maxLen=")) config.maxStringLength = Integer.parseInt(arg.substring("--maxLen=".length()));
        }
        return config;
    }

    private static List<String> splitLongString(String input, int maxLength) {
        List<String> parts = new ArrayList<>();
        for (int start = 0; start < input.length(); start += maxLength) {
            int end = Math.min(start + maxLength, input.length());
            parts.add(input.substring(start, end));
        }
        return parts;
    }

    public static void main(String[] args) throws Exception {
        ApplicationConfig config = parseCommandLineArgs(args);
        System.out.printf("Configuration: type=%s workers=%d delays: step=%dms swap=%dms maxLength=%d%n",
                config.listType, config.workerCount, config.stepDelayMs, config.swapDelayMs, config.maxStringLength);

        AtomicLong operationCounter = new AtomicLong();
        List<Thread> sorterThreads = new ArrayList<>();

        if ("custom_linked".equalsIgnoreCase(config.listType)) {
            LinkedStringList stringList = new LinkedStringList();
            List<LinkedListBubbleSorter> sorters = new ArrayList<>();
            for (int threadId = 0; threadId < config.workerCount; threadId++) {
                LinkedListBubbleSorter sorter = new LinkedListBubbleSorter(stringList, config.stepDelayMs, config.swapDelayMs, operationCounter);
                Thread workerThread = new Thread(sorter, "linked-sorter-" + threadId);
                sorters.add(sorter);
                sorterThreads.add(workerThread);
                workerThread.start();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String inputLine;
                while ((inputLine = reader.readLine()) != null) {
                    if (inputLine.isEmpty()) {
                        System.out.println("--- Current list state (size=" + stringList.size() + ", operations=" + operationCounter.get() + ") ---");
                        for (String item : stringList) System.out.println(item);
                        System.out.println("--- End of list ---");
                    } else if (":stats".equals(inputLine)) {
                        System.out.println("Total operations performed: " + operationCounter.get());
                    } else {
                        List<String> stringParts = splitLongString(inputLine, config.maxStringLength);
                        for (int partIndex = stringParts.size() - 1; partIndex >= 0; partIndex--) {
                            stringList.addFirst(stringParts.get(partIndex));
                        }
                    }
                }
            } finally {
                for (Thread worker : sorterThreads) worker.interrupt();
                for (Thread worker : sorterThreads) worker.join();
            }
        } else if ("arraylist_sync".equalsIgnoreCase(config.listType)) {
            List<String> backingList = new ArrayList<>();
            List<String> synchronizedList = Collections.synchronizedList(backingList);
            List<ArrayListBubbleSorter> sorters = new ArrayList<>();
            for (int threadId = 0; threadId < config.workerCount; threadId++) {
                ArrayListBubbleSorter sorter = new ArrayListBubbleSorter(synchronizedList, config.stepDelayMs, config.swapDelayMs, operationCounter);
                Thread workerThread = new Thread(sorter, "array-sorter-" + threadId);
                sorters.add(sorter);
                sorterThreads.add(workerThread);
                workerThread.start();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String inputLine;
                while ((inputLine = reader.readLine()) != null) {
                    if (inputLine.isEmpty()) {
                        int currentSize;
                        synchronized (synchronizedList) { currentSize = synchronizedList.size(); }
                        System.out.println("--- Current list state (size=" + currentSize + ", operations=" + operationCounter.get() + ") ---");
                        synchronized (synchronizedList) {
                            for (String item : synchronizedList) System.out.println(item);
                        }
                        System.out.println("--- End of list ---");
                    } else if (":stats".equals(inputLine)) {
                        System.out.println("Total operations performed: " + operationCounter.get());
                    } else {
                        List<String> stringParts = splitLongString(inputLine, config.maxStringLength);
                        for (int partIndex = stringParts.size() - 1; partIndex >= 0; partIndex--) {
                            synchronized (synchronizedList) { synchronizedList.add(0, stringParts.get(partIndex)); }
                        }
                    }
                }
            } finally {
                for (Thread worker : sorterThreads) worker.interrupt();
                for (Thread worker : sorterThreads) worker.join();
            }
        } else {
            System.err.println("Unsupported list type: " + config.listType);
        }
    }
}
