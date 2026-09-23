package com.techpix.fraud.internal.strangler;

import com.techpix.fraud.internal.FraudProperties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O pool que roda o Fraud novo em shadow.
 * <p>
 * Separado das threads HTTP de propósito: se o Fraud Service ficar lento, o shadow enfileira
 * e descarta; as threads que atendem clientes nunca esperam por ele. Fila pequena e política
 * de descarte: shadow perdido é estatística, não incidente.
 */
@Configuration
public class ShadowExecutorConfig {

    @Bean(name = "shadowExecutor", destroyMethod = "shutdownNow")
    ThreadPoolExecutor shadowExecutor(FraudProperties properties) {
        int threads = properties.parallel().threads();
        return new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(properties.parallel().queueSize()),
                r -> {
                    Thread t = new Thread(r, "fraud-shadow");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardPolicy());
    }
}
