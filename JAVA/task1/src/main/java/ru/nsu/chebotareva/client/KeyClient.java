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

/**
 * Клиент для получения RSA ключей от сервера генерации ключей
 */
public class KeyClient implements Callable<Integer> {

    private String serverHost;
    private int serverPort;
    private String clientName;
    private int responseDelaySeconds = 0;
    private boolean shouldAbortAfterRequest;
    private Path outputDirectory = Path.of(".");

    private static void displayUsageInstructions() {
        System.out.println("Usage: key-client --host <host> --port <port> --name <name> [--delay <sec>] [--abort] [--out <dir>]");
        System.out.println("Command line options:");
        System.out.println("  -h, --host    Server hostname or IP address (required)");
        System.out.println("  -p, --port    Server TCP port number (required)");
        System.out.println("  -n, --name    Client identifier (ASCII string, null-terminated) (required)");
        System.out.println("  -d, --delay   Pause in seconds before retrieving server response (default: 0)");
        System.out.println("  -a, --abort   Terminate connection after sending request without waiting for response");
        System.out.println("  -o, --out     Directory for saving .key and .crt files (default: current directory)");
    }

    private static KeyClient parseCommandLineArguments(String[] args) {
        KeyClient client = new KeyClient();
        for (int i = 0; i < args.length; i++) {
            String currentArg = args[i];
            switch (currentArg) {
                case "-h":
                case "--host":
                    if (i + 1 >= args.length) {
                        System.err.println("--host requires a parameter value");
                        displayUsageInstructions();
                        System.exit(2);
                    }
                    client.serverHost = args[++i];
                    break;
                case "-p":
                case "--port":
                    if (i + 1 >= args.length) {
                        System.err.println("--port requires a parameter value");
                        displayUsageInstructions();
                        System.exit(2);
                    }
                    try {
                        client.serverPort = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException ex) {
                        System.err.println("--port must be a valid integer");
                        System.exit(2);
                    }
                    break;
                case "-n":
                case "--name":
                    if (i + 1 >= args.length) {
                        System.err.println("--name requires a parameter value");
                        displayUsageInstructions();
                        System.exit(2);
                    }
                    client.clientName = args[++i];
                    break;
                case "-d":
                case "--delay":
                    if (i + 1 >= args.length) {
                        System.err.println("--delay requires a parameter value");
                        displayUsageInstructions();
                        System.exit(2);
                    }
                    try {
                        client.responseDelaySeconds = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException ex) {
                        System.err.println("--delay must be a valid integer");
                        System.exit(2);
                    }
                    break;
                case "-a":
                case "--abort":
                    client.shouldAbortAfterRequest = true;
                    break;
                case "-o":
                case "--out":
                    if (i + 1 >= args.length) {
                        System.err.println("--out requires a parameter value");
                        displayUsageInstructions();
                        System.exit(2);
                    }
                    client.outputDirectory = Paths.get(args[++i]);
                    break;
                case "-?":
                case "-help":
                case "--help":
                    displayUsageInstructions();
                    System.exit(0);
                    break;
                default:
                    System.err.println("Unrecognized option: " + currentArg);
                    displayUsageInstructions();
                    System.exit(2);
            }
        }
        if (client.serverHost == null || client.serverPort == 0 || client.clientName == null) {
            System.err.println("Required parameters are missing");
            displayUsageInstructions();
            System.exit(2);
        }
        return client;
    }

    @Override
    public Integer call() {
        System.out.println("[KeyClient] Initializing client connection...");
        System.out.printf("[KeyClient] Server: %s:%d, Name: %s, Delay: %ds, Abort: %s, Output: %s%n",
                serverHost, serverPort, clientName, responseDelaySeconds,
                shouldAbortAfterRequest, outputDirectory.toAbsolutePath());

        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            System.err.println("[KeyClient] Unable to create output directory: " + outputDirectory + ": " + e.getMessage());
            return 1;
        }

        try (Socket serverSocket = new Socket(serverHost, serverPort)) {
            serverSocket.setTcpNoDelay(true);
            OutputStream outputStream = serverSocket.getOutputStream();
            InputStream inputStream = serverSocket.getInputStream();

            byte[] clientNameBytes = clientName.getBytes(Protocol.NAME_CHARSET);
            outputStream.write(clientNameBytes);
            outputStream.write(Protocol.NAME_TERMINATOR);
            outputStream.flush();

            if (shouldAbortAfterRequest) {
                System.out.println("[KeyClient] Terminating connection after request transmission as requested.");
                return 0;
            }

            if (responseDelaySeconds > 0) {
                try {
                    Thread.sleep(responseDelaySeconds * 1000L);
                } catch (InterruptedException ignored) {}
            }

            int privateKeyLength = readIntBE(inputStream);
            byte[] privateKeyPem = (privateKeyLength > 0) ? readExact(inputStream, privateKeyLength) : new byte[0];
            int certificateLength = readIntBE(inputStream);
            byte[] certificatePem = (certificateLength > 0) ? readExact(inputStream, certificateLength) : new byte[0];

            if (privateKeyLength == 0 && certificateLength == 0) {
                System.err.println("[KeyClient] Server responded with error indicators (lengths are zero)");
                return 2;
            }

            Path privateKeyFile = outputDirectory.resolve(clientName + ".key");
            Path certificateFile = outputDirectory.resolve(clientName + ".crt");
            Files.write(privateKeyFile, privateKeyPem);
            Files.write(certificateFile, certificatePem);
            System.out.printf("[KeyClient] Key files saved: %s and %s%n",
                           privateKeyFile.toAbsolutePath(), certificateFile.toAbsolutePath());
            return 0;
        } catch (IOException e) {
            System.err.println("[KeyClient] Network communication error: " + e.getMessage());
            return 1;
        }
    }

    private static int readIntBE(InputStream inputStream) throws IOException {
        byte[] buffer = readExact(inputStream, 4);
        return ((buffer[0] & 0xFF) << 24) | ((buffer[1] & 0xFF) << 16) |
               ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
    }

    private static byte[] readExact(InputStream inputStream, int requiredLength) throws IOException {
        byte[] buffer = new byte[requiredLength];
        int bytesRead = 0;
        while (bytesRead < requiredLength) {
            int chunkSize = inputStream.read(buffer, bytesRead, requiredLength - bytesRead);
            if (chunkSize < 0) {
                throw new IOException("Connection terminated prematurely while reading " +
                                    bytesRead + "/" + requiredLength + " bytes");
            }
            bytesRead += chunkSize;
        }
        return buffer;
    }

    public static void main(String[] args) {
        KeyClient client = parseCommandLineArguments(args);
        int exitCode = 0;
        try {
            exitCode = client.call();
        } catch (Exception e) {
            e.printStackTrace();
            exitCode = 1;
        }
        System.exit(exitCode);
    }
}
