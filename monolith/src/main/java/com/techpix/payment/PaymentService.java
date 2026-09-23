package com.techpix.payment;

import com.techpix.account.Account;
import com.techpix.account.AccountService;
import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudResult;
import com.techpix.fraud.FraudService;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * O caso de uso central da Tech Pix. Uma única transação de banco cobre
 * validação, fraude, ledger e notificação. É simples e, hoje, isso é uma virtude.
 * <p>
 * A partir da etapa 3, cada pagamento registra: tempo total, tempo de Fraud e consultas ao banco.
 * Uma linha de log por pagamento responde "onde o tempo foi gasto?".
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
    private final FraudService fraud;
    private final LedgerService ledger;
    private final NotificationService notifications;
    private final Clock clock;
    private final Timer paymentTimer;
    private final DistributionSummary paymentQueries;

    public PaymentService(PaymentRepository payments, AccountService accounts, FraudService fraud,
                          LedgerService ledger, NotificationService notifications, Clock clock, MeterRegistry metrics) {
        this.payments = payments;
        this.accounts = accounts;
        this.fraud = fraud;
        this.ledger = ledger;
        this.notifications = notifications;
        this.clock = clock;
        this.paymentTimer = Timer.builder("payment.create").description("Tempo total de POST /payments").register(metrics);
        this.paymentQueries = DistributionSummary.builder("payment.queries")
                .description("Instrucoes SQL executadas por pagamento").register(metrics);
    }

    @Transactional
    public PaymentOutcome create(CreatePayment command) {
        long start = System.nanoTime();
        QueryCounter.reset();

        Account payer = accounts.requireActive(command.payerAccountId());
        accounts.requireActive(command.payeeAccountId());
        if (payer.balance().compareTo(command.amount()) < 0) {
            throw DomainException.unprocessable("insufficient funds on account " + payer.id());
        }

        Instant now = Instant.now(clock);
        Payment payment = new Payment(UUID.randomUUID(), command.payerAccountId(), command.payeeAccountId(),
                command.amount(), command.deviceId(), PaymentStatus.PENDING, null, now);
        payments.insert(payment);

        FraudResult result = fraud.evaluate(new FraudCheck(payment.id(), payment.payerAccountId(),
                payment.payeeAccountId(), payment.amount(), payment.deviceId(), now));

        if (result.rejected()) {
            String reason = "fraud score " + result.score() + " rules " + result.triggeredRules();
            payments.updateStatus(payment.id(), PaymentStatus.REJECTED, reason);
            notifications.notify(payment.payerAccountId(), "Payment " + payment.id() + " rejected");
        } else {
            accounts.transfer(payment.payerAccountId(), payment.payeeAccountId(), payment.amount());
            ledger.record(payment.id(), payment.payerAccountId(), payment.payeeAccountId(), payment.amount());
            payments.updateStatus(payment.id(), PaymentStatus.APPROVED, null);
            notifications.notify(payment.payerAccountId(), "Payment " + payment.id() + " approved");
            notifications.notify(payment.payeeAccountId(), "You received " + payment.amount());
        }

        Payment stored = payments.findById(payment.id()).orElseThrow();
        long totalMs = (System.nanoTime() - start) / 1_000_000;
        long queries = QueryCounter.current();
        paymentTimer.record(totalMs, TimeUnit.MILLISECONDS);
        paymentQueries.record(queries);
        log.info("payment={} status={} score={} totalMs={} fraudMs={} queries={} fraudRules={}",
                stored.id(), stored.status(), result.score(), totalMs, result.durationMs(), queries, result.rulesEvaluated());
        return new PaymentOutcome(stored, result, totalMs, queries);
    }

    public Payment get(UUID id) {
        return payments.findById(id).orElseThrow(() -> DomainException.notFound("payment " + id + " not found"));
    }
}
