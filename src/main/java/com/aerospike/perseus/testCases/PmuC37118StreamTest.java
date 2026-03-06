package com.aerospike.perseus.testCases;

import com.aerospike.perseus.data.PmuFrame;
import com.aerospike.perseus.data.c37118.C37118Encoder;
import com.aerospike.perseus.data.generators.PmuFrameGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * IEEE C37.118 binary protocol streaming test.
 * Connects to Hitachi's TCP receiver and streams real C37.118 DATA frames.
 *
 * Each thread maintains its own TCP connection (via ThreadLocal).
 * On first execute(), connects and sends CFG-2 config frame.
 * Subsequent calls send DATA frames at the rate driven by the generator.
 */
public class PmuC37118StreamTest extends Test<PmuFrame> {

    private final C37118Encoder encoder;
    private final String receiverHost;
    private final int receiverPort;
    private final ThreadLocal<SocketContext> socketCtx = new ThreadLocal<>();

    public PmuC37118StreamTest(TestCaseConstructorArguments arguments,
                               PmuFrameGenerator generator,
                               C37118Encoder encoder,
                               String receiverHost, int receiverPort) {
        super(arguments, generator);
        this.encoder = encoder;
        this.receiverHost = receiverHost;
        this.receiverPort = receiverPort;
    }

    @Override
    protected void execute(PmuFrame frame) {
        try {
            SocketContext ctx = ensureConnected(frame);
            byte[] encoded = encoder.encodeDataFrame(frame);
            ctx.outputStream.write(encoded);
            ctx.outputStream.flush();
        } catch (IOException e) {
            // Connection lost — close and let next call reconnect
            closeContext();
            throw new RuntimeException("C37.118 TCP write failed: " + e.getMessage(), e);
        }
    }

    private SocketContext ensureConnected(PmuFrame frame) throws IOException {
        SocketContext ctx = socketCtx.get();
        if (ctx != null && !ctx.socket.isClosed()) {
            return ctx;
        }

        System.out.println("[C37118] Connecting to " + receiverHost + ":" + receiverPort +
                " from thread " + Thread.currentThread().getName());

        Socket socket = new Socket(receiverHost, receiverPort);
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        OutputStream out = socket.getOutputStream();

        ctx = new SocketContext(socket, out);
        socketCtx.set(ctx);

        // Send CFG-2 configuration frame before any data
        byte[] cfg2 = encoder.encodeCfg2Frame(frame.getTsMicros());
        out.write(cfg2);
        out.flush();

        System.out.println("[C37118] Connected and sent CFG-2 (" + cfg2.length +
                " bytes) from thread " + Thread.currentThread().getName());
        return ctx;
    }

    private void closeContext() {
        SocketContext ctx = socketCtx.get();
        if (ctx != null) {
            try {
                ctx.socket.close();
            } catch (IOException ignored) {}
            socketCtx.remove();
        }
    }

    @Override
    public String[] getHeader() {
        return "C37.118\nStream".split("\n");
    }

    private static class SocketContext {
        final Socket socket;
        final OutputStream outputStream;

        SocketContext(Socket socket, OutputStream outputStream) {
            this.socket = socket;
            this.outputStream = outputStream;
        }
    }
}
