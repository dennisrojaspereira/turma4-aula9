package com.techpix.payment;

public enum PaymentStatus {
    PENDING,
    APPROVED,
    /** Fraud disse nao. */
    REJECTED,
    /** Fraud disse sim, mas a liquidacao falhou (ledger/banco). Nao e culpa do pagador. Compensado por PaymentFailed. */
    FAILED
}
