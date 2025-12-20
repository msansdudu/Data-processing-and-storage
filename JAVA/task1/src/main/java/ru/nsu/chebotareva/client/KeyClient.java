package ru.nsu.chebotareva.client;

import ru.nsu.chebotareva.common.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

public class KeyClient implements Callable<Integer> {

    private String host;
    private int port;
    private String name;
    private int delaySeconds = 0;
    private boolean abort;
    private Path outDir = Path.of(".");

    private static void printUsage() {
        System.out.println("Usage: key-client --host <host> --port <port> --name <name> [--delay <sec>] [--abort] [--out <dir>]");
        System.out.println("Options:");
        System.out.println("  -h, --host    Server host or DNS name (required)");
        System.out.println("  -p, --port    Server TCP port (required)");
        System.out.println("  -n, --name    Client name (ASCII), terminated by NUL on the wire (required)");
        System.out.println("  -d, --delay   Delay in seconds before reading the response (default: 0)");
        System.out.println("  -a, --abort   Abort after sending the name (do not read the response)");
        System.out.println("  -o, --out     Output directory to save .key and .crt files (default: .)");
    }

    private static KeyClient parseArgs(String[] args) {
        KeyClient c = new KeyClient();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h":
                case "--host":
                    if (i + 1 >= args.length) { System.err.println("--host requires a value"); printUsage(); System.exit(2); }
                    c.host = args[++i];
                    break;
                case "-p":
                case "--port":
                    if (i + 1 >= args.length) { System.err.println("--port requires a value"); printUsage(); System.exit(2); }
                    try { c.port = Integer.parseInt(args[++i]); }
                    catch (NumberFormatException ex) { System.err.println("--port must be an integer"); System.exit(2); }
                    break;
                case "-n":
                case "--name":
                    if (i + 1 >= args.length) { System.err.println("--name requires a value"); printUsage(); System.exit(2); }
                    c.name = args[++i];
                    break;
                case "-d":
                case "--delay":
                    if (i + 1 >= args.length) { System.err.println("--delay requires a value"); printUsage(); System.exit(2); }
                    try { c.delaySeconds = Integer.parseInt(args[++i]); }
                    catch (NumberFormatException ex) { System.err.println("--delay must be an integer"); System.exit(2); }
                    break;
                case "-a":
                case "--abort":
                    c.abort = true;
                    break;
                case "-o":
                case "--out":
                    if (i + 1 >= args.length) { System.err.println("--out requires a value"); printUsage(); System.exit(2); }
                    c.outDir = Paths.get(args[++i]);
                    break;
                case "-?":
                case "-help":
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;
                default:
                    System.err.println("Unknown option: " + a);
                    printUsage();
                    System.exit(2);
            }
        }
        if (c.host == null || c.port == 0 || c.name == null) {
            System.err.println("Missing required options");
            printUsage();
            System.exit(2);
        }
        return c;
    }

    @Override
    public Integer call() {
        System.out.println("[KeyClient] Starting...");
        System.out.printf("[KeyClient] host=%s port=%d name=%s delay=%ds abort=%s out=%s%n",
                host, port, name, delaySeconds, abort, outDir.toAbsolutePath());

        try {
            Files.createDirectories(outDir);
        } catch (IOException e) {
            System.err.println("[KeyClient] Failed to create output directory: " + outDir + ": " + e.getMessage());
            return 1;
        }

        try (Socket socket = new Socket(host, port)) {
            socket.setTcpNoDelay(true);
            OutputStream os = socket.getOutputStream();
            InputStream is = socket.getInputStream();

            byte[] nameBytes = name.getBytes(Protocol.NAME_CHARSET);
            os.write(nameBytes);
            os.write(Protocol.NAME_TERMINATOR);
            os.flush();

            if (abort) {
                System.out.println("[KeyClient] Abort requested after sending name. Closing.");
                return 0;
            }

            if (delaySeconds > 0) {
                try { Thread.sleep(delaySeconds * 1000L); } catch (InterruptedException ignored) {}
            }

            int lenKey = readIntBE(is);
            byte[] keyPem = (lenKey > 0) ? readExact(is, lenKey) : new byte[0];
            int lenCert = readIntBE(is);
            byte[] certPem = (lenCert > 0) ? readExact(is, lenCert) : new byte[0];

            if (lenKey == 0 && lenCert == 0) {
                System.err.println("[KeyClient] Server returned error lengths (0,0)");
                return 2;
            }

            Path keyPath = outDir.resolve(name + ".key");
            Path crtPath = outDir.resolve(name + ".crt");
            Files.write(keyPath, keyPem);
            Files.write(crtPath, certPem);
            System.out.printf("[KeyClient] Saved %s and %s%n", keyPath.toAbsolutePath(), crtPath.toAbsolutePath());
            return 0;
        } catch (IOException e) {
            System.err.println("[KeyClient] I/O error: " + e.getMessage());
            return 1;
        }
    }

    private static int readIntBE(InputStream is) throws IOException {
        byte[] b = readExact(is, 4);
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
        
    }

    private static byte[] readExact(InputStream is, int len) throws IOException {
        byte[] buf = new byte[len];
        int off = 0;
        while (off < len) {
            int r = is.read(buf, off, len - off);
            if (r < 0) throw new IOException("Connection closed while reading " + off + "/" + len + " bytes");
            off += r;
        }
        return buf;
    }

    public static void main(String[] args) {
        KeyClient client = parseArgs(args);
        int exit = 0;
        try {
            exit = client.call();
        } catch (Exception e) {
            e.printStackTrace();
            exit = 1;
        }
        System.exit(exit);
    }
}
