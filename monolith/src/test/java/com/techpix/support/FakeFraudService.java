package com.techpix.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Um Fraud Service falso, em 60 linhas, sem dependência extra: o HttpServer do JDK.
 * <p>
 * Serve para testar o lado do Payment: o que acontece quando o serviço responde, demora,
 * devolve erro ou some. O comportamento é programável por teste.
 */
public class FakeFraudService {

    public record Behaviour(int status, String body, long delayMs) {
        public static Behaviour ok(int score, String decision) {
            return new Behaviour(200, """
                    {"paymentId":"00000000-0000-0000-0000-000000000000","score":%d,"decision":"%s",
                     "triggeredRules":["fake"],"rulesEvaluated":1,"durationMs":1}
                    """.formatted(score, decision), 0);
        }

        public static Behaviour error(int status) {
            return new Behaviour(status, "{\"error\":\"boom\"}", 0);
        }

        public Behaviour withDelay(long delayMs) {
            return new Behaviour(status, body, delayMs);
        }
    }

    private final HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicLong lastCallNanos = new AtomicLong();
    private volatile Function<Integer, Behaviour> script = call -> Behaviour.ok(0, "APPROVED");

    public FakeFraudService() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/fraud/evaluations", exchange -> {
            int call = calls.incrementAndGet();
            lastCallNanos.set(System.nanoTime());
            requests.add(json.readTree(exchange.getRequestBody()));
            Behaviour b = script.apply(call);
            if (b.delayMs() > 0) {
                try {
                    Thread.sleep(b.delayMs());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = b.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(b.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.createContext("/actuator/health", exchange -> {
            byte[] bytes = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Mesmo comportamento para toda chamada. */
    public void respond(Behaviour behaviour) {
        script = call -> behaviour;
    }

    /** Comportamento por número da chamada (1-based): permite "falha, falha, sucesso". */
    public void respondBySequence(Function<Integer, Behaviour> byCall) {
        script = byCall;
    }

    public int calls() {
        return calls.get();
    }

    public List<JsonNode> requests() {
        return requests;
    }

    public void reset() {
        calls.set(0);
        requests.clear();
        respond(Behaviour.ok(0, "APPROVED"));
    }

    public void stop() {
        server.stop(0);
    }
}
