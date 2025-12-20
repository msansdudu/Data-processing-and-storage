package ru.nsu.chebotareva.server;

import ru.nsu.chebotareva.common.Protocol;
import ru.nsu.chebotareva.common.CryptoUtil;
import ru.nsu.chebotareva.common.PemUtil;
import ru.nsu.chebotareva.common.KeyData;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Сервер генерации RSA ключей с использованием NIO и пула генераторов
 */
public class KeyServer {
    private final int serverPort;
    private final int generatorThreadCount;
    private final String certificateIssuer;
    private final PrivateKey signingKey;

    private final ExecutorService keyGenerationPool;
    private final ConcurrentHashMap<String, CompletableFuture<KeyData>> keyCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<DeliveryTask> deliveryQueue = new ConcurrentLinkedQueue<>();

    /**
     * Задача доставки сгенерированных ключей клиенту
     */
    private static class DeliveryTask {
        final SelectionKey clientKey;
        final KeyData keyMaterial;

        DeliveryTask(SelectionKey clientKey, KeyData keyMaterial) {
            this.clientKey = clientKey;
            this.keyMaterial = keyMaterial;
        }
    }

    public KeyServer(int port, int threads, String issuerDn, PrivateKey issuerKey) {
        this.serverPort = port;
        this.generatorThreadCount = threads;
        this.certificateIssuer = issuerDn;
        this.signingKey = issuerKey;
        this.keyGenerationPool = Executors.newFixedThreadPool(this.generatorThreadCount);
    }

    private static void printUsage() {
        System.out.println("Usage: key-server --port <port> --threads <N> --issuer <DN> --key <path-to-issuer-private-pem>");
        System.out.println("Options:");
        System.out.println("  -p, --port     Server TCP port (required)");
        System.out.println("      --threads  Generator thread count (required)");
        System.out.println("      --issuer   Issuer DN, e.g. CN=KeyIssuer,O=NSU (required)");
        System.out.println("      --key      Issuer private key PEM path (required)");
        System.out.println("  -?, --help    Show this help");
    }

    private static class Config {
        int port;
        int threads;
        String issuerDn;
        Path keyPath;
    }

    private static Config parseArgs(String[] args) {
        Config cfg = new Config();
        Integer port = null; Integer threads = null; String issuer = null; Path keyPath = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-p":
                case "--port":
                    if (i + 1 >= args.length) { System.err.println("--port requires a value"); printUsage(); System.exit(2); }
                    try { port = Integer.parseInt(args[++i]); } catch (NumberFormatException ex) { System.err.println("--port must be an integer"); System.exit(2); }
                    break;
                case "--threads":
                    if (i + 1 >= args.length) { System.err.println("--threads requires a value"); printUsage(); System.exit(2); }
                    try { threads = Integer.parseInt(args[++i]); } catch (NumberFormatException ex) { System.err.println("--threads must be an integer"); System.exit(2); }
                    break;
                case "--issuer":
                    if (i + 1 >= args.length) { System.err.println("--issuer requires a value"); printUsage(); System.exit(2); }
                    issuer = args[++i];
                    break;
                case "--key":
                    if (i + 1 >= args.length) { System.err.println("--key requires a value"); printUsage(); System.exit(2); }
                    keyPath = Paths.get(args[++i]);
                    break;
                case "-?":
                case "--help":
                case "-help":
                    printUsage();
                    System.exit(0);
                    break;
                default:
                    System.err.println("Unknown option: " + a);
                    printUsage();
                    System.exit(2);
            }
        }
        if (port == null || port == 0 || threads == null || threads <= 0 || issuer == null || keyPath == null) {
            System.err.println("Missing required options");
            printUsage();
            System.exit(2);
        }
        cfg.port = port; cfg.threads = threads; cfg.issuerDn = issuer; cfg.keyPath = keyPath; return cfg;
    }

    public int run() throws Exception {
        System.out.printf("[KeyServer] Starting on port %d with %d generator threads, issuer='%s'%n",
                        serverPort, generatorThreadCount, certificateIssuer);

        try (Selector selector = Selector.open();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(serverPort));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            Map<SocketChannel, ClientConnection> activeConnections = new HashMap<>();

            while (true) {
                selector.select();

                // Обработка очереди доставки результатов генерации
                DeliveryTask deliveryItem;
                while ((deliveryItem = deliveryQueue.poll()) != null) {
                    if (deliveryItem.keyMaterial != null) {
                        prepareResponseForClient(deliveryItem.clientKey, activeConnections,
                                               createSuccessResponse(deliveryItem.keyMaterial));
                    } else {
                        prepareResponseForClient(deliveryItem.clientKey, activeConnections,
                                               createErrorResponse());
                    }
                }

                Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey currentKey = selectedKeys.next();
                    selectedKeys.remove();
                    try {
                        if (!currentKey.isValid()) continue;

                        if (currentKey.isAcceptable()) {
                            SocketChannel clientChannel = serverChannel.accept();
                            if (clientChannel != null) {
                                clientChannel.configureBlocking(false);
                                ClientConnection connection = new ClientConnection();
                                SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ, connection);
                                connection.associatedKey = clientKey;
                                activeConnections.put(clientChannel, connection);
                            }
                        } else if (currentKey.isReadable()) {
                            SocketChannel clientChannel = (SocketChannel) currentKey.channel();
                            ClientConnection connection = (ClientConnection) currentKey.attachment();
                            if (connection == null) {
                                closeClientConnection(clientChannel, activeConnections);
                                continue;
                            }
                            int bytesRead = clientChannel.read(connection.inputBuffer);
                            if (bytesRead == -1) { // клиент закрыл соединение
                                closeClientConnection(clientChannel, activeConnections);
                                continue;
                            }
                            connection.inputBuffer.flip();
                            processClientRequest(currentKey, activeConnections, selector);
                            connection.inputBuffer.compact();

                        } else if (currentKey.isWritable()) {
                            SocketChannel clientChannel = (SocketChannel) currentKey.channel();
                            ClientConnection connection = (ClientConnection) currentKey.attachment();
                            if (connection == null || connection.outputBuffer == null) {
                                currentKey.interestOps(SelectionKey.OP_READ);
                                    continue;
                            }
                            clientChannel.write(connection.outputBuffer);
                            if (!connection.outputBuffer.hasRemaining()) {
                                connection.outputBuffer = null;
                                currentKey.interestOps(0);
                                closeClientConnection(clientChannel, activeConnections);
                            }
                        }
                    } catch (CancelledKeyException ignored) {
                    } catch (IOException io) {
                        Channel ch = currentKey.channel();
                        if (ch instanceof SocketChannel sc) {
                            closeClientConnection(sc, activeConnections);
                        }
                    }
                }
            }
        }
    }

    private static void closeClientConnection(SocketChannel ch, Map<SocketChannel, ClientConnection> map) {
        map.remove(ch);
        try { ch.close(); } catch (IOException ignored) {}
    }

    private void prepareResponseForClient(SelectionKey clientKey, Map<SocketChannel, ClientConnection> connections, byte[] responseData) {
        SocketChannel clientChannel = (SocketChannel) clientKey.channel();
        ClientConnection connection = connections.get(clientChannel);
        if (connection != null) {
            connection.outputBuffer = ByteBuffer.wrap(responseData);
            clientKey.interestOps(SelectionKey.OP_WRITE);
        }
    }

    private static class ClientConnection {
        final ByteBuffer inputBuffer = ByteBuffer.allocate(Protocol.MAX_NAME_LEN);
        final ArrayList<Byte> receivedNameBytes = new ArrayList<>();
        boolean nameReceptionComplete = false;
        boolean requestLogged = false;
        SelectionKey associatedKey;
        ByteBuffer outputBuffer;

        String extractClientName(Charset charset) {
            byte[] nameArray = new byte[receivedNameBytes.size()];
            for (int i = 0; i < receivedNameBytes.size(); i++) {
                nameArray[i] = receivedNameBytes.get(i);
            }
            return new String(nameArray, charset).trim();
        }
    }

    private void processClientRequest(SelectionKey clientKey, Map<SocketChannel, ClientConnection> connections, Selector selector) {
        ClientConnection connection = (ClientConnection) clientKey.attachment();
        ByteBuffer buffer = connection.inputBuffer;

        while (buffer.hasRemaining() && !connection.nameReceptionComplete) {
            byte currentByte = buffer.get();
            if (currentByte == Protocol.NAME_TERMINATOR) {
                connection.nameReceptionComplete = true;
                break;
            }
            // Проверка на допустимые ASCII символы
            if (currentByte < 0x20 || currentByte > 0x7E) {
                if (currentByte != 0x20) {
                    System.out.println("[KeyServer] Invalid character in client name. Terminating connection.");
                    SocketChannel ch = (SocketChannel) clientKey.channel();
                    closeClientConnection(ch, connections);
                    return;
                }
            }
            if (connection.receivedNameBytes.size() >= Protocol.MAX_NAME_LEN) {
                System.out.println("[KeyServer] Client name exceeds maximum length. Terminating connection.");
                SocketChannel ch = (SocketChannel) clientKey.channel();
                closeClientConnection(ch, connections);
                return;
            }
            connection.receivedNameBytes.add(currentByte);
        }

        if (connection.nameReceptionComplete && !connection.requestLogged) {
            connection.requestLogged = true;
            String clientName = connection.extractClientName(Protocol.NAME_CHARSET);
            SocketChannel clientChannel = (SocketChannel) clientKey.channel();
            System.out.printf("[KeyServer] Processing request for name: '%s' from %s%n", clientName, clientChannel);

            CompletableFuture<KeyData> keyFuture = keyCache.computeIfAbsent(clientName, name ->
                CompletableFuture.supplyAsync(() -> generateKeyPairForClient(name), keyGenerationPool)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            keyCache.remove(name);
                        }
                    })
            );

            keyFuture.thenAccept(keyData -> {
                deliveryQueue.add(new DeliveryTask(clientKey, keyData));
                selector.wakeup();
            }).exceptionally(throwable -> {
                deliveryQueue.add(new DeliveryTask(clientKey, null)); // null indicates error
                selector.wakeup();
                return null;
            });
        }
    }

    private static void writeIntBE(ByteBuffer buf, int v) {
        buf.put((byte)((v >>> 24) & 0xFF));
        buf.put((byte)((v >>> 16) & 0xFF));
        buf.put((byte)((v >>> 8) & 0xFF));
        buf.put((byte)(v & 0xFF));
    }

    private static byte[] createSuccessResponse(KeyData keyData) {
        byte[] privateKeyPem = keyData.getPrivateKeyPem();
        byte[] certificatePem = keyData.getCertificatePem();
        ByteBuffer responseBuffer = ByteBuffer.allocate(
            Protocol.LENGTH_FIELD_BYTES + privateKeyPem.length +
            Protocol.LENGTH_FIELD_BYTES + certificatePem.length
        );
        writeIntBE(responseBuffer, privateKeyPem.length);
        responseBuffer.put(privateKeyPem);
        writeIntBE(responseBuffer, certificatePem.length);
        responseBuffer.put(certificatePem);
        return responseBuffer.array();
    }

    private static byte[] createErrorResponse() {
        ByteBuffer responseBuffer = ByteBuffer.allocate(Protocol.LENGTH_FIELD_BYTES * 2);
        writeIntBE(responseBuffer, Protocol.ERROR_LENGTH);
        writeIntBE(responseBuffer, Protocol.ERROR_LENGTH);
        return responseBuffer.array();
    }



    private KeyData generateKeyPairForClient(String clientName) {
        try {
            KeyPair rsaKeyPair = CryptoUtil.generateRsa8192();
            X509Certificate x509Certificate = CryptoUtil.issueCertificate(certificateIssuer, clientName,
                                                                       rsaKeyPair.getPublic(), signingKey);
            byte[] privateKeyPem = PemUtil.privateKeyToPemBytes(rsaKeyPair.getPrivate());
            byte[] certificatePem = PemUtil.certificateToPemBytes(x509Certificate);
            return new KeyData(privateKeyPem, certificatePem);
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    public static void main(String[] args) {
        Config cfg = parseArgs(args);
        int exit = 0;
        try {
            PrivateKey issuerKey = CryptoUtil.loadPrivateKeyFromPem(cfg.keyPath);
            exit = new KeyServer(cfg.port, cfg.threads, cfg.issuerDn, issuerKey).run();
        } catch (Exception e) {
            e.printStackTrace();
            exit = 1;
        }
        System.exit(exit);
    }
}
