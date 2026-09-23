package com.techpix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Tech Pix: um único processo com Account, Payment, Ledger, Fraud e Notification.
 * <p>
 * Não existe problema arquitetural aqui. Um monólito que atende 10 TPS com p95 de 180 ms
 * é uma decisão válida. Simplicidade também é arquitetura.
 */
@SpringBootApplication
public class TechPixApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechPixApplication.class, args);
    }
}
