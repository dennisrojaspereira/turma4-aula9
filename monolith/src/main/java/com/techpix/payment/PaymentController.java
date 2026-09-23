package com.techpix.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    public record CreatePaymentRequest(
            @NotNull UUID payerAccountId,
            @NotNull UUID payeeAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String deviceId) {
    }

    public record PaymentResponse(
            UUID id,
            PaymentStatus status,
            int fraudScore,
            List<String> fraudRules,
            long fraudDurationMs,
            String rejectionReason,
            Instant createdAt) {
    }

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentService.PaymentOutcome outcome = service.create(new PaymentService.CreatePayment(
                request.payerAccountId(), request.payeeAccountId(), request.amount(), request.deviceId()));
        Payment p = outcome.payment();
        return new PaymentResponse(p.id(), p.status(), outcome.fraud().score(), outcome.fraud().triggeredRules(),
                outcome.fraud().durationMs(), p.rejectionReason(), p.createdAt());
    }

    @GetMapping("/{id}")
    public Payment get(@PathVariable UUID id) {
        return service.get(id);
    }
}
