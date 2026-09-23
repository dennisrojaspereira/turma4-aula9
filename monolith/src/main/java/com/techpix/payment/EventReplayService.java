package com.techpix.payment;

import com.techpix.account.AccountService;
import com.techpix.payment.internal.events.PaymentEventPublisher;
import com.techpix.shared.DomainException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Republica o fato de um pagamento existente com o mesmo eventId. Ver {@code EventReplayController}. */
@Service
public class EventReplayService {

    private final PaymentService payments;
    private final AccountService accounts;
    private final PaymentEventPublisher events;

    public EventReplayService(PaymentService payments, AccountService accounts, PaymentEventPublisher events) {
        this.payments = payments;
        this.accounts = accounts;
        this.events = events;
    }

    public Map<String, Object> replay(UUID paymentId) {
        Payment payment = payments.get(paymentId);
        if (payment.status() == PaymentStatus.PENDING) {
            throw DomainException.unprocessable("payment " + paymentId + " is still PENDING; nothing to replay");
        }
        PaymentEvent.Type type = switch (payment.status()) {
            case APPROVED -> PaymentEvent.Type.PaymentApproved;
            case REJECTED -> PaymentEvent.Type.PaymentRejected;
            case FAILED -> PaymentEvent.Type.PaymentFailed;
            case PENDING -> throw new IllegalStateException("unreachable");
        };
        PaymentEvent event = PaymentEvent.of(type, payment, accounts.get(payment.payerAccountId()).createdAt());
        events.publish(event);
        return Map.of("replayed", event.type(), "eventId", event.eventId(), "paymentId", paymentId);
    }
}
