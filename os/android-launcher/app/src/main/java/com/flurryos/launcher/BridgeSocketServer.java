package com.flurryos.launcher;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** JSON Lines server bound to Android's abstract local-socket namespace. */
public final class BridgeSocketServer implements AutoCloseable {
    public interface Handler {
        JSONObject handle(JSONObject request);
    }

    private static final int MAX_LINE_BYTES = 64 * 1024;
    private final String socketName;
    private final Handler handler;
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile LocalServerSocket server;
    private Thread acceptThread;

    public BridgeSocketServer(String socketName, Handler handler) {
        if (socketName == null || socketName.length() == 0 || socketName.length() > 100) {
            throw new IllegalArgumentException("nombre de socket inválido");
        }
        this.socketName = socketName;
        this.handler = handler;
    }

    public synchronized void start() throws IOException {
        if (running.get()) return;
        server = new LocalServerSocket(socketName);
        running.set(true);
        acceptThread = new Thread(new Runnable() {
            @Override public void run() { acceptLoop(); }
        }, "flurryos-bridge-accept");
        acceptThread.start();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                final LocalSocket client = server.accept();
                clients.execute(new Runnable() {
                    @Override public void run() { serve(client); }
                });
            } catch (IOException exception) {
                if (running.get()) System.err.println("Bridge accept error: " + exception.getMessage());
                break;
            }
        }
    }

    private void serve(LocalSocket client) {
        try (LocalSocket socket = client;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null || line.getBytes(StandardCharsets.UTF_8).length > MAX_LINE_BYTES) {
                writer.write(error("", "REQUEST_TOO_LARGE", "solicitud ausente o demasiado grande").toString());
                writer.newLine();
                writer.flush();
                return;
            }
            JSONObject response;
            try {
                response = handler.handle(new JSONObject(line));
            } catch (Exception exception) {
                response = error("", "INVALID_REQUEST", exception.getMessage() == null ? "JSON inválido" : exception.getMessage());
            }
            writer.write(response.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            System.err.println("Bridge client error: " + exception.getMessage());
        }
    }

    public synchronized void close() {
        if (!running.getAndSet(false)) return;
        LocalServerSocket current = server;
        server = null;
        if (current != null) {
            try { current.close(); } catch (IOException ignored) { }
        }
        clients.shutdownNow();
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
    }

    public static JSONObject error(String id, String code, String message) {
        JSONObject response = new JSONObject();
        try {
            response.put("version", 1);
            response.put("id", id == null ? "" : id);
            response.put("ok", false);
            JSONObject error = new JSONObject();
            error.put("code", code);
            error.put("message", message);
            response.put("error", error);
        } catch (Exception impossible) { throw new AssertionError(impossible); }
        return response;
    }
}
