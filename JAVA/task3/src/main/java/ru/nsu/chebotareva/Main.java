package ru.nsu.chebotareva;

import java.net.http.HttpClient;
import java.util.List;
import ru.nsu.chebotareva.http.HttpFactory;
import ru.nsu.chebotareva.crawler.AsyncCrawler;
import ru.nsu.chebotareva.cli.Args;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        Args.Config config = Args.parse(args);
        HttpClient client = HttpFactory.create(Duration.ofSeconds(15));
        System.out.println("Crawler Configuration:\n" +
                "  Server URL: " + config.baseUrl() + "\n" +
                "  Verbose mode: " + config.verbose() + "\n" +
                "  Max concurrent connections: " + config.maxConcurrency() + "\n" +
                "  Total timeout (seconds): " + config.totalTimeoutSec() +
                (config.outPath() != null ? "\n  Output file: " + config.outPath() : ""));
        if (config.verbose()) {
            System.out.println("HTTP client ready");
        }

        try {
            AsyncCrawler crawler = new AsyncCrawler(client);
            List<String> results = crawler.crawl(
                    config.baseUrl(),
                    config.verbose(),
                    config.maxConcurrency(),
                    Duration.ofSeconds(config.totalTimeoutSec()));

            if (config.outPath() != null) {
                Path outputPath = Path.of(config.outPath());
                Files.write(outputPath, results);
                System.out.println("Saved " + results.size() + " messages to " + outputPath.toAbsolutePath());
            } else {
                for (String message : results) {
                    System.out.println(message);
                }
            }
        } catch (Exception e) {
            System.err.println("Crawling failed: " + e.getMessage());
            System.exit(1);
        }
    }
}