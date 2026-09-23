package com.techpix.payment;

import com.techpix.payment.internal.PaymentRepository;
import com.techpix.payment.internal.events.PaymentEventPublisher;
import com.techpix.account.Account;
import com.techpix.account.AccountService;
import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudResult;
import com.techpix.fraud.FraudUnavailableException;
import com.techpix.fraud.FraudEvaluator;
import com.techpix.ledger.LedgerService;
import com.techpix.notification.NotificationService;
import com.techpix.shared.DomainException;
import com.techpix.shared.observability.QueryCounter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O caso de uso central da Tech Pix.
 * <p>
 * Até a etapa 3, uma única transação cobria tudo, inclusive a avaliação de Fraud. O lab 03 mostrou
 * o custo disso: cada conexão do pool ficava presa durante toda a avaliação, inclusive durante os
 * 30 ms em que a thread dormia esperando o provider externo.
 * <p>
 * A partir da etapa 4 o fluxo tem três fases:
 * <ol>
 *   <li>transação curta: validar e registrar o pagamento como PENDING;</li>
 *   <li>fora de transação: avaliar fraude (cada consulta pega e devolve a conexão);</li>
 *   <li>transação curta: liquidar (transferência, ledger, status) ou rejeitar.</li>
 * </ol>
 * Trade-off: se o processo cair entre as fases, sobra um pagamento PENDING. Isso é novo. Antes
 * não existia. É o primeiro sinal de que "uma transação para tudo" era uma garantia que estávamos
 * pagando caro para manter.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public record CreatePayment(UUID payerAccountId, UUID payeeAccountId, BigDecimal amount, String deviceId) {
    }

    public record PaymentOutcome(Payment payment, FraudResult fraud, long totalMs, long queries) {
    }

    private final PaymentRepository payments;
    private final AccountService accounts;
    private final FraudEvaluator fraud;
    private final LedgerService ledger;
    private final NotificationService notifications;
    private final Clock clock;
    private final PaymentEventPublisher events;
    private final TransactionTemplate tx;
    private final Timer paymentTimer;
    private final DistributionSummary paymentQueries;

    public PaymentService(PaymentRepository payments, AccountService accounts, FraudEvaluator fraud,
                          LedgerService ledger, NotificationService notifications, Clock clock,
                          PaymentEventPublisher events, PlatformTransactionManager transactionManager, MeterRegistry metrics) {
        this.payments = payments;
        this.accounts = accounts;
        this.fraud = fraud;
        this.ledger = ledger;
        this.notifications = notifications;
        this.clock = clock;
        this.events = events;
        this.tx = new TransactionTemplate(transactionManager);
        this.paymentTimer = Timer.builder("payment.create").description("Tempo total de POST /payments").register(metrics);
        this.paymentQueries = DistributionSummary.builder("payment.queries")
                .description("Instrucoes SQL executadas por pagamento").register(metrics);
    }

    public PaymentOutcome create(CreatePayment command) {
        long start = System.nanoTime();
        QueryCounter.reset();

        // Fase 1: transação curta. Valida e registra a intenção de pagar.
        Payment payment = tx.execute(status -> registerPending(command));

        // Fase 2: sem transação. Fraud lê histórico e grava sua avaliação; nenhuma conexão fica presa entre consultas.
        FraudResult result;
        try {
            result = fraud.evaluate(new FraudCheck(payment.id(), payment.payerAccountId(),
                    payment.payeeAccountId(), payment.amount(), payment.deviceId(), payment.createdAt()));
        } catch (FraudUnavailableException e) {
            // Fail closed: sem decisão de Fraud, nenhum dinheiro se move. O cliente recebe 503 e pode tentar de novo.
            // Antes da extração este bloco não existia. Uma chamada de método não "ficava indisponível".
            tx.executeWithoutResult(status -> payments.updateStatus(payment.id(), PaymentStatus.REJECTED, "fraud unavailable"));
            log.warn("payment={} status=REJECTED reason=fraud-unavailable detail={}", payment.id(), e.getMessage());
            throw e;
        }

        // Fase 3: transação curta. Liquida ou rejeita.
        Payment stored;
        try {
            stored = tx.execute(status -> result.rejected() ? reject(payment, result) : settle(payment));
        } catch (LedgerService.LedgerUnavailableException e) {
            // Saga, na forma mais simples que existe: transacao local falhou DEPOIS de outro servico (Fraud) ja
            // ter registrado este pagamento no banco dele. Nao ha rollback distribuido. Ha compensacao:
            // marcamos FAILED aqui e publicamos o fato; Fraud ouve e desfaz o que registrou.
            tx.executeWithoutResult(status -> payments.updateStatus(payment.id(), PaymentStatus.FAILED, "ledger: " + e.getMessage()));
            Payment failed = payments.findById(payment.id()).orElseThrow();
            events.publish(PaymentEvent.of(PaymentEvent.Type.PaymentFailed, failed, accounts.get(failed.payerAccountId()).createdAt()));
            log.error("payment={} status=FAILED reason=ledger-unavailable compensation=PaymentFailed", payment.id());
            throw new DomainException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "payment failed: " + e.getMessage());
        }

        // Fase 4: conta ao mundo o que aconteceu. Depois do commit: um fato só é publicado se for verdade.
        events.publish(PaymentEvent.of(
                stored.status() == PaymentStatus.APPROVED ? PaymentEvent.Type.PaymentApproved : PaymentEvent.Type.PaymentRejected,
                stored, accounts.get(stored.payerAccountId()).createdAt()));

        long totalMs = (System.nanoTime() - start) / 1_000_000;
        long queries = QueryCounter.current();
        paymentTimer.record(totalMs, TimeUnit.MILLISECONDS);
        paymentQueries.record(queries);
        log.info("payment={} status={} score={} totalMs={} fraudMs={} queries={} fraudRules={}",
                stored.id(), stored.status(), result.score(), totalMs, result.durationMs(), queries, result.rulesEvaluated());
        return new PaymentOutcome(stored, result, totalMs, queries);
    }

    private Payment registerPending(CreatePayment command) {
        Account payer = accounts.requireActive(command.payerAccountId());
        accounts.requireActive(command.payeeAccountId());
        if (payer.balance().compareTo(command.amount()) < 0) {
            throw DomainException.unprocessable("insufficient funds on account " + payer.id());
        }
        Payment payment = new Payment(UUID.randomUUID(), command.payerAccountId(), command.payeeAccountId(),
                command.amount(), command.deviceId(), PaymentStatus.PENDING, null, Instant.now(clock));
        payments.insert(payment);
        return payment;
    }

    private Payment reject(Payment payment, FraudResult result) {
        String reason = "fraud score " + result.score() + " rules " + result.triggeredRules();
        payments.updateStatus(payment.id(), PaymentStatus.REJECTED, reason);
        notifications.notify(payment.payerAccountId(), "Payment " + payment.id() + " rejected");
        return payments.findById(payment.id()).orElseThrow();
    }

    private Payment settle(Payment payment) {
        accounts.transfer(payment.payerAccountId(), payment.payeeAccountId(), payment.amount());
        ledger.record(payment.id(), payment.payerAccountId(), payment.payeeAccountId(), payment.amount());
        payments.updateStatus(payment.id(), PaymentStatus.APPROVED, null);
        notifications.notify(payment.payerAccountId(), "Payment " + payment.id() + " approved");
        notifications.notify(payment.payeeAccountId(), "You received " + payment.amount());
        return payments.findById(payment.id()).orElseThrow();
    }

    public Payment get(UUID id) {
        return payments.findById(id).orElseThrow(() -> DomainException.notFound("payment " + id + " not found"));
    }
}
