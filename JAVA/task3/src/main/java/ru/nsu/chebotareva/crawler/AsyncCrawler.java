package ru.nsu.chebotareva.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.nsu.chebotareva.json.Json;
import ru.nsu.chebotareva.model.ResponseDto;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncCrawler {
    private final HttpClient http;
    private final ObjectMapper mapper;

    public AsyncCrawler(HttpClient http) {
        this.http = http;
        this.mapper = Json.mapper();
    }

    public List<String> crawl(URI baseUrl, boolean verbose, int maxConcurrency, Duration totalTimeout) throws InterruptedException {
        Set<String> processedPaths = ConcurrentHashMap.newKeySet();
        ConcurrentLinkedQueue<String> collectedMessages = new ConcurrentLinkedQueue<>();
        Semaphore requestLimiter = new Semaphore(Math.max(1, maxConcurrency));
        AtomicInteger activeRequests = new AtomicInteger(0);
        AtomicBoolean isCancelled = new AtomicBoolean(false);
        AtomicInteger processedCount = new AtomicInteger(0);
        Instant timeoutDeadline = Instant.now().plus(totalTimeout);
        final int maxPaths = 1000; // Ограничение на максимальное количество обрабатываемых путей

        ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        try {
            processPath(executor, baseUrl, "/", processedPaths, collectedMessages, requestLimiter, activeRequests, processedCount, maxPaths, verbose, timeoutDeadline, isCancelled);

            while (true) {
                if (activeRequests.get() == 0) break;
                if (processedCount.get() >= maxPaths) {
                    isCancelled.set(true);
                    if (verbose) System.err.println("Maximum path limit reached (" + maxPaths + "), stopping remaining requests");
                    break;
                }
                if (Instant.now().isAfter(timeoutDeadline)) {
                    isCancelled.set(true);
                    if (verbose) System.err.println("Timeout exceeded, stopping remaining requests");
                    break;
                }
                Thread.sleep(10);
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        ArrayList<String> result = new ArrayList<>(collectedMessages);
        Collections.sort(result);
        return result;
    }

    private void processPath(ExecutorService executor,
                            URI baseUrl,
                            String path,
                            Set<String> processedPaths,
                            ConcurrentLinkedQueue<String> collectedMessages,
                            Semaphore requestLimiter,
                            AtomicInteger activeRequests,
                            AtomicInteger processedCount,
                            int maxPaths,
                            boolean verbose,
                            Instant timeoutDeadline,
                            AtomicBoolean isCancelled) {
        String normalizedPath = normalizePath(path);
        if (!processedPaths.add(normalizedPath)) return;
        if (processedCount.incrementAndGet() > maxPaths) return;
        activeRequests.incrementAndGet();

        executor.submit(() -> {
            try {
                if (isCancelled.get() || Instant.now().isAfter(timeoutDeadline)) return;

                requestLimiter.acquire();
                try {
                    if (isCancelled.get() || Instant.now().isAfter(timeoutDeadline)) return;

                    URI fullUri = baseUrl.resolve(normalizedPath);
                    if (verbose) System.out.println("Requesting: " + fullUri);

                    HttpRequest request = HttpRequest.newBuilder(fullUri)
                            .GET()
                            .timeout(Duration.ofSeconds(15))
                            .build();

                    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                    if (verbose) System.out.println("Response status: " + response.statusCode());

                    if (response.statusCode() == 200 && response.body() != null) {
                        ResponseDto responseData = mapper.readValue(response.body(), ResponseDto.class);

                        if (responseData.getMessage() != null) {
                            collectedMessages.add(responseData.getMessage());
                        }

                        List<String> nextPaths = responseData.getSuccessors();
                        if (nextPaths != null) {
                            for (String nextPath : nextPaths) {
                                if (isCancelled.get() || Instant.now().isAfter(timeoutDeadline)) break;
                                processPath(executor, baseUrl, nextPath, processedPaths, collectedMessages,
                                           requestLimiter, activeRequests, processedCount, maxPaths, verbose, timeoutDeadline, isCancelled);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (verbose) System.err.println("Request error: " + e.getMessage());
                } finally {
                    requestLimiter.release();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                activeRequests.decrementAndGet();
            }
        });
    }

    private static String normalizePath(String p) {
        if (p == null) return "/";
        String s = p.trim();
        if (s.isEmpty()) return "/";
        if (!s.startsWith("/")) s = "/" + s;
        return s;
    }
}
