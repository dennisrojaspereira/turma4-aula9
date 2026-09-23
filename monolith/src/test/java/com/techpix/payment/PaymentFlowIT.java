package com.techpix.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.techpix.account.AccountService;
import com.techpix.ledger.LedgerEntry;
import com.techpix.ledger.LedgerService;
import com.techpix.notification.NotificationService;
import com.techpix.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** O fluxo completo, ponta a ponta, contra um PostgreSQL real. */
class PaymentFlowIT extends AbstractIntegrationTest {

    @Autowired
    AccountService accounts;
    @Autowired
    LedgerService ledger;
    @Autowired
    NotificationService notifications;

    @Test
    void approvedPaymentMovesMoneyAndWritesLedger() {
        UUID alice = openAccount("Alice", "1000.00");
        UUID bob = openAccount("Bob", "0.00");

        ResponseEntity<Map> response = pay(alice, bob, "250.00", "device-alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("APPROVED");
        UUID paymentId = UUID.fromString((String) response.getBody().get("id"));

        assertThat(accounts.get(alice).balance()).isEqualByComparingTo(new BigDecimal("750.00"));
        assertThat(accounts.get(bob).balance()).isEqualByComparingTo(new BigDecimal("250.00"));

        List<LedgerEntry> entries = ledger.entriesOf(paymentId);
        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(LedgerEntry::type)
                .containsExactlyInAnyOrder(LedgerEntry.EntryType.DEBIT, LedgerEntry.EntryType.CREDIT);

        assertThat(notifications.messagesOf(alice)).anyMatch(m -> m.contains("approved"));
        assertThat(notifications.messagesOf(bob)).anyMatch(m -> m.contains("received"));
    }

    @Test
    void payingYourselfIsRejectedByFraudAndNothingMoves() {
        UUID carol = openAccount("Carol", "500.00");

        ResponseEntity<Map> response = pay(carol, carol, "10.00", "device-carol");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("REJECTED");
        assertThat(response.getBody().get("fraudScore")).isEqualTo(100);
        assertThat(accounts.get(carol).balance()).isEqualByComparingTo(new BigDecimal("500.00"));
        UUID paymentId = UUID.fromString((String) response.getBody().get("id"));
        assertThat(ledger.entriesOf(paymentId)).isEmpty();
    }

    @Test
    void insufficientFundsIsUnprocessable() {
        UUID dave = openAccount("Dave", "10.00");
        UUID erin = openAccount("Erin", "0.00");

        ResponseEntity<Map> response = pay(dave, erin, "20.00", "device-dave");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(accounts.get(dave).balance()).isEqualByComparingTo(new BigDecimal("10.00"));
    }
}
