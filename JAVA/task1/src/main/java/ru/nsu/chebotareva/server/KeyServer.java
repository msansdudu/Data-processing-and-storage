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

public class KeyServer {
    private final int port;
    private final int threads;
    private final String issuerDn;
    private final PrivateKey issuerKey;

    private final ExecutorService generatorPool;
    private final ConcurrentHashMap<String, CompletableFuture<KeyData>> cache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Runnable> pending = new ConcurrentLinkedQueue<>();

    public KeyServer(int port, int threads, String issuerDn, PrivateKey issuerKey) {
        this.port = port;
        this.threads = threads;
        this.issuerDn = issuerDn;
        this.issuerKey = issuerKey;
        this.generatorPool = Executors.newFixedThreadPool(this.threads);
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
        System.out.printf("[KeyServer] Starting on port %d, threads=%d, issuer='%s'%n", port, threads, issuerDn);

        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open()) {
            server.configureBlocking(false);
            server.bind(new InetSocketAddress(port));
            server.register(selector, SelectionKey.OP_ACCEPT);

            Map<SocketChannel, ConnState> states = new HashMap<>();

            while (true) {
                selector.select();
                for (Runnable r; (r = pending.poll()) != null; ) r.run();
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    try {
                        if (!key.isValid()) continue;
                        if (key.isAcceptable()) {
                            SocketChannel ch = server.accept();
                            if (ch != null) {
                                ch.configureBlocking(false);
                                ConnState st = new ConnState();
                                SelectionKey k2 = ch.register(selector, SelectionKey.OP_READ, st);
                                st.key = k2;
                                states.put(ch, st);
                            }
                        } else if (key.isReadable()) {
                            SocketChannel ch = (SocketChannel) key.channel();
                            ConnState st = (ConnState) key.attachment();
                            if (st == null) {
                                closeQuiet(ch, states);
                                continue;
                            }
                            int r = ch.read(st.buf);
                            if (r == -1) { // client closed
                                closeQuiet(ch, states);
                                continue;
                            }
                            st.buf.flip();
                            while (st.buf.hasRemaining() && !st.nameCompleted) {
                                byte b = st.buf.get();
                                if (b == Protocol.NAME_TERMINATOR) {
                                    st.nameCompleted = true;
                                    break;
                                }
                                // enforce ASCII printable (plus space) and max length
                                if (b < 0x20 || b > 0x7E) {
                                    if (b != 0x20) {
                                        System.out.println("[KeyServer] Invalid character in name. Closing.");
                                        closeQuiet(ch, states);
                                        st.buf.compact();
                                        continue;
                                    }
                                }
                                if (st.nameBytes.size() >= Protocol.MAX_NAME_LEN) {
                                    System.out.println("[KeyServer] Name is too long. Closing.");
                                    closeQuiet(ch, states);
                                    st.buf.compact();
                                    continue;
                                }
                                st.nameBytes.add(b);
                            }
                            st.buf.compact();

                            if (st.nameCompleted && !st.logged) {
                                st.logged = true;
                                String name = st.getName(Protocol.NAME_CHARSET);
                                System.out.printf("[KeyServer] Received name: '%s' from %s%n", name, ch);
                                CompletableFuture<KeyData> fut = cache.computeIfAbsent(name, n ->
                                        CompletableFuture.supplyAsync(() -> generateForName(n), generatorPool)
                                                .whenComplete((res, ex) -> { if (ex != null) cache.remove(n); })
                                );
                                fut.whenComplete((data, ex) -> {
                                    if (ex != null) {
                                        enqueue(() -> prepareAndStartWrite(key, states, errorResponse()));
                                    } else {
                                        enqueue(() -> prepareAndStartWrite(key, states, buildResponse(data)));
                                    }
                                    selector.wakeup();
                                });
                            }
                        } else if (key.isWritable()) {
                            SocketChannel ch = (SocketChannel) key.channel();
                            ConnState st = (ConnState) key.attachment();
                            if (st == null || st.out == null) { key.interestOps(SelectionKey.OP_READ); continue; }
                            ch.write(st.out);
                            if (!st.out.hasRemaining()) {
                                st.out = null;
                                key.interestOps(0);
                                closeQuiet(ch, states);
                            }
                        }
                    } catch (CancelledKeyException ignored) {
                    } catch (IOException io) {
                        Channel ch = key.channel();
                        if (ch instanceof SocketChannel sc) {
                            closeQuiet(sc, states);
                        }
                    }
                }
            }
        }
    }

    private static void closeQuiet(SocketChannel ch, Map<SocketChannel, ConnState> map) {
        map.remove(ch);
        try { ch.close(); } catch (IOException ignored) {}
    }

    private static class ConnState {
        final ByteBuffer buf = ByteBuffer.allocate(Protocol.MAX_NAME_LEN);
        final ArrayList<Byte> nameBytes = new ArrayList<>();
        boolean nameCompleted = false;
        boolean logged = false;
        SelectionKey key;
        ByteBuffer out;

        String getName(Charset cs) {
            byte[] a = new byte[nameBytes.size()];
            for (int i = 0; i < nameBytes.size(); i++) a[i] = nameBytes.get(i);
            return new String(a, cs).trim();
        }
    }

    private static void writeIntBE(ByteBuffer buf, int v) {
        buf.put((byte)((v >>> 24) & 0xFF));
        buf.put((byte)((v >>> 16) & 0xFF));
        buf.put((byte)((v >>> 8) & 0xFF));
        buf.put((byte)(v & 0xFF));
    }

    private static byte[] buildResponse(KeyData data) {
        byte[] k = data.getPrivateKeyPem();
        byte[] c = data.getCertificatePem();
        ByteBuffer b = ByteBuffer.allocate(Protocol.LENGTH_FIELD_BYTES + k.length + Protocol.LENGTH_FIELD_BYTES + c.length);
        writeIntBE(b, k.length);
        b.put(k);
        writeIntBE(b, c.length);
        b.put(c);
        return b.array();
    }

    private static byte[] errorResponse() {
        ByteBuffer b = ByteBuffer.allocate(Protocol.LENGTH_FIELD_BYTES * 2);
        writeIntBE(b, Protocol.ERROR_LENGTH);
        writeIntBE(b, Protocol.ERROR_LENGTH);
        return b.array();
    }

    private void enqueue(Runnable r) { pending.add(r); }

    private void prepareAndStartWrite(SelectionKey key, Map<SocketChannel, ConnState> states, byte[] payload) {
        SocketChannel ch = (SocketChannel) key.channel();
        ConnState st = (ConnState) key.attachment();
        if (st == null) return;
        st.out = ByteBuffer.wrap(payload);
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private KeyData generateForName(String name) {
        try {
            KeyPair kp = CryptoUtil.generateRsa8192();
            X509Certificate cert = CryptoUtil.issueCertificate(issuerDn, name, kp.getPublic(), issuerKey);
            byte[] keyPem = PemUtil.privateKeyToPemBytes(kp.getPrivate());
            byte[] certPem = PemUtil.certificateToPemBytes(cert);
            return new KeyData(keyPem, certPem);
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
