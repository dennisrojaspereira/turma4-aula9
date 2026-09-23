package com.techpix.payment.internal.events;

import com.techpix.payment.PaymentEvent;

/** Porta de saída de Payment: "aconteceu isto". Quem escuta e como chega lá não é problema de Payment. */
public interface PaymentEventPublisher {

    void publish(PaymentEvent event);
}
