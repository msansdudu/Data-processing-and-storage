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
        Set<String> visited = ConcurrentHashMap.newKeySet();
        ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();
        Semaphore permits = new Semaphore(Math.max(1, maxConcurrency));
        AtomicInteger inFlight = new AtomicInteger(0);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Instant deadline = Instant.now().plus(totalTimeout);

        ExecutorService exec = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        try {
            submit(exec, baseUrl, "/", visited, messages, permits, inFlight, verbose, deadline, cancelled);

            while (true) {
                if (inFlight.get() == 0) break;
                if (Instant.now().isAfter(deadline)) {
                    cancelled.set(true);
                    if (verbose) System.err.println("Total timeout reached, cancelling remaining tasks");
                    break;
                }
                Thread.sleep(5);
            }
        } finally {
            exec.shutdown();
            exec.awaitTermination(5, TimeUnit.SECONDS);
        }

        ArrayList<String> out = new ArrayList<>(messages);
        Collections.sort(out);
        return out;
    }

    private void submit(ExecutorService exec,
                        URI baseUrl,
                        String path,
                        Set<String> visited,
                        ConcurrentLinkedQueue<String> messages,
                        Semaphore permits,
                        AtomicInteger inFlight,
                        boolean verbose,
                        Instant deadline,
                        AtomicBoolean cancelled) {
        String norm = normalizePath(path);
        if (!visited.add(norm)) return;
        inFlight.incrementAndGet();

        exec.submit(() -> {
            try {
                if (cancelled.get() || Instant.now().isAfter(deadline)) return;
                if (!permits.tryAcquire(50, TimeUnit.MILLISECONDS)) {
                    permits.acquire();
                }
                try {
                    if (cancelled.get() || Instant.now().isAfter(deadline)) return;
                    URI uri = baseUrl.resolve(norm);
                    if (verbose) System.out.println("GET " + uri);
                    HttpRequest req = HttpRequest.newBuilder(uri)
                            .GET()
                            .timeout(Duration.ofSeconds(15))
                            .build();
                    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                    if (verbose) System.out.println("<- status=" + resp.statusCode());
                    if (resp.statusCode() != 200 || resp.body() == null) return;

                    ResponseDto dto = mapper.readValue(resp.body(), ResponseDto.class);
                    if (dto.getMessage() != null) messages.add(dto.getMessage());
                    List<String> succ = dto.getSuccessors();
                    if (succ != null) {
                        for (String s : succ) {
                            if (cancelled.get() || Instant.now().isAfter(deadline)) break;
                            submit(exec, baseUrl, s, visited, messages, permits, inFlight, verbose, deadline, cancelled);
                        }
                    }
                } catch (Exception e) {
                    if (verbose) System.err.println("Error: " + e.getMessage());
                } finally {
                    permits.release();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
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
