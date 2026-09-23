package com.techpix.payment;

import com.techpix.account.Account;
import com.techpix.account.AccountService;
import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudResult;
import com.techpix.fraud.FraudService;
import com.techpix.ledger.LedgerService;
import com.techpix.notification.NotificationService;
import com.techpix.shared.DomainException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O caso de uso central da Tech Pix. Uma única transação de banco cobre
 * validação, fraude, ledger e notificação. É simples e, hoje, isso é uma virtude.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public record CreatePayment(UUID payerAccountId, UUID payeeAccountId, BigDecimal amount, String deviceId) {
    }

    public record PaymentOutcome(Payment payment, FraudResult fraud) {
    }

    private final PaymentRepository payments;
    private final AccountService accounts;
    private final FraudService fraud;
    private final LedgerService ledger;
    private final NotificationService notifications;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, AccountService accounts, FraudService fraud,
                          LedgerService ledger, NotificationService notifications, Clock clock) {
        this.payments = payments;
        this.accounts = accounts;
        this.fraud = fraud;
        this.ledger = ledger;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public PaymentOutcome create(CreatePayment command) {
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
            log.info("payment={} status=REJECTED score={}", payment.id(), result.score());
            return new PaymentOutcome(payments.findById(payment.id()).orElseThrow(), result);
        }

        accounts.transfer(payment.payerAccountId(), payment.payeeAccountId(), payment.amount());
        ledger.record(payment.id(), payment.payerAccountId(), payment.payeeAccountId(), payment.amount());
        payments.updateStatus(payment.id(), PaymentStatus.APPROVED, null);
        notifications.notify(payment.payerAccountId(), "Payment " + payment.id() + " approved");
        notifications.notify(payment.payeeAccountId(), "You received " + payment.amount());
        log.info("payment={} status=APPROVED score={}", payment.id(), result.score());
        return new PaymentOutcome(payments.findById(payment.id()).orElseThrow(), result);
    }

    public Payment get(UUID id) {
        return payments.findById(id).orElseThrow(() -> DomainException.notFound("payment " + id + " not found"));
    }
}
