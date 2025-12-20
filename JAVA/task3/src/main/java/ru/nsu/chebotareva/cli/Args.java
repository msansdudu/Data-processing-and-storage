package ru.nsu.chebotareva.cli;

import java.net.URI;
import java.net.URISyntaxException;

public final class Args {
    private Args() {}

    public static record Config(URI baseUrl, boolean verbose, int maxConcurrency, int totalTimeoutSec, String outPath) {}

    public static void printHelp() {
        System.out.println("Usage: java -jar task3.jar [options]\n" +
                "Options:\n" +
                "  --baseUrl <url>   Base server URL (default: http://localhost:8080)\n" +
                "  --verbose|-v      Verbose logging\n" +
                "  --maxConcurrency <n>      Max concurrent requests (default: 64)\n" +
                "  --totalTimeoutSec <n>     Total crawl timeout in seconds (default: 120)\n" +
                "  --out <path>              Write sorted messages to file instead of stdout\n" +
                "  --help|-h         Show this help and exit");
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
