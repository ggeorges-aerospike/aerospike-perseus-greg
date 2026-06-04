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
    private final long intervalMs;  // sleep between frames for real-time pacing
    private final ThreadLocal<SocketContext> socketCtx = new ThreadLocal<>();

    public PmuC37118StreamTest(TestCaseConstructorArguments arguments,
                               PmuFrameGenerator generator,
                               C37118Encoder encoder,
                               String receiverHost, int receiverPort, int fps) {
        super(arguments, generator);
        this.encoder = encoder;
        this.receiverHost = receiverHost;
        this.receiverPort = receiverPort;
        this.intervalMs = (fps > 0) ? 1000L / fps : 0;
    }

    @Override
    protected void execute(PmuFrame frame) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                SocketContext ctx = ensureConnected(frame);
                byte[] encoded = encoder.encodeDataFrame(frame);
                ctx.outputStream.write(encoded);
                ctx.outputStream.flush();

                // Pace to real-time FPS (e.g., 5ms sleep for 200 FPS)
                if (intervalMs > 0) {
                    Thread.sleep(intervalMs);
                }

                return; // success
            } catch (IOException e) {
                closeContext();
                if (attempt < 4) {
                    try { Thread.sleep(1000L * (attempt + 1)); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
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

        // Send CFG-2 configuration frame before any data.
        // Use per-stream IDCODE derived from the stream ID so the receiver
        // creates a stable stream identity that survives reconnections.
        int streamIdcode = extractStreamIdcode(frame.getStreamId());
        byte[] cfg2 = encoder.encodeCfg2Frame(frame.getTsMicros(), streamIdcode);
        out.write(cfg2);
        out.flush();

        System.out.println("[C37118] Connected and sent CFG-2 (" + cfg2.length +
                " bytes, IDCODE=" + streamIdcode + ") from thread " + Thread.currentThread().getName());
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

    private static int extractStreamIdcode(String streamId) {
        try {
            int idx = Integer.parseInt(streamId.replaceAll("[^0-9]", ""));
            return idx + 1; // 1-based, IDCODE 0 is reserved in C37.118
        } catch (NumberFormatException e) {
            return 1;
        }
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
