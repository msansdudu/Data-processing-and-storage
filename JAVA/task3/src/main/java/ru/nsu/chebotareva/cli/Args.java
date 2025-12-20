package ru.nsu.chebotareva.cli;

import java.net.URI;
import java.net.URISyntaxException;

public final class Args {
    private Args() {}

    public static record Config(URI baseUrl, boolean verbose, int maxConcurrency, int totalTimeoutSec, String outPath) {}

    public static void printHelp() {
        System.out.println("Web Crawler - Usage: java -jar crawler.jar [options]\n" +
                "Available options:\n" +
                "  --baseUrl <url>           Server base URL (default: http://localhost:8080)\n" +
                "  --verbose|-v              Enable detailed output\n" +
                "  --maxConcurrency <n>      Maximum concurrent HTTP requests (default: 64)\n" +
                "  --totalTimeoutSec <n>     Total operation timeout in seconds (default: 120)\n" +
                "  --out <path>              Save results to file instead of console output\n" +
                "  --help|-h                 Display this help message");
    }

    private static void die(String msg) {
        System.err.println(msg);
        System.err.println();
        printHelp();
        System.exit(1);
    }

    public static Config parse(String[] args) {
        URI baseUrl = URI.create("http://localhost:8080");
        boolean verbose = false;
        int maxConcurrency = 64;
        int totalTimeoutSec = 120;
        String outPath = null;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--help":
                case "-h":
                    printHelp();
                    System.exit(0);
                    break;
                case "--verbose":
                case "-v":
                    verbose = true;
                    break;
                case "--baseUrl":
                    if (i + 1 >= args.length) die("Missing value for --baseUrl");
                    String v = args[++i];
                    try {
                        baseUrl = new URI(v);
                    } catch (URISyntaxException e) {
                        die("Invalid --baseUrl: " + v);
                    }
                    break;
                case "--maxConcurrency":
                    if (i + 1 >= args.length) die("Missing value for --maxConcurrency");
                    maxConcurrency = parsePositiveInt(args[++i], "--maxConcurrency");
                    break;
                case "--totalTimeoutSec":
                    if (i + 1 >= args.length) die("Missing value for --totalTimeoutSec");
                    totalTimeoutSec = parsePositiveInt(args[++i], "--totalTimeoutSec");
                    break;
                case "--out":
                    if (i + 1 >= args.length) die("Missing value for --out");
                    outPath = args[++i];
                    break;
                default:
                    die("Unknown option: " + a);
            }
        }
        return new Config(baseUrl, verbose, maxConcurrency, totalTimeoutSec, outPath);
    }

    private static int parsePositiveInt(String s, String opt) {
        try {
            int v = Integer.parseInt(s);
            if (v <= 0) die(opt + " must be > 0");
            return v;
        } catch (NumberFormatException e) {
            die("Invalid integer for " + opt + ": " + s);
            return -1;
        }
    }
}
