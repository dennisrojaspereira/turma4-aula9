package com.techpix.fraudservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Fraud Service: um processo que responde uma pergunta, "este pagamento é suspeito?".
 * <p>
 * Nasceu do módulo Fraud do monólito, mas é um projeto próprio: pacote próprio, vocabulário próprio,
 * configuração própria, ciclo de deploy próprio. O que ele ainda compartilha com o monólito
 * (o banco) está isolado atrás de uma Anti-Corruption Layer, em {@code acl}.
 */
@SpringBootApplication
@EnableScheduling
public class FraudServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudServiceApplication.class, args);
    }
}
