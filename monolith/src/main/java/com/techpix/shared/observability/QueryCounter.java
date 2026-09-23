package com.techpix.shared.observability;

/**
 * Conta quantas instruções SQL a thread atual executou.
 * <p>
 * É a ferramenta mais barata e mais reveladora deste laboratório: "quantas consultas custa um pagamento?"
 * é uma pergunta que quase ninguém sabe responder sem medir.
 */
public final class QueryCounter {

    private static final ThreadLocal<long[]> COUNT = ThreadLocal.withInitial(() -> new long[1]);

    private QueryCounter() {
    }

    public static void increment() {
        COUNT.get()[0]++;
    }

    public static long current() {
        return COUNT.get()[0];
    }

    public static void reset() {
        COUNT.get()[0] = 0;
    }
}
