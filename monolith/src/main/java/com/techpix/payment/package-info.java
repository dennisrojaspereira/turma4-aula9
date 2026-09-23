/**
 * Módulo Payment: orquestra um pagamento de ponta a ponta.
 * <p>
 * É o único módulo que conhece os outros (Account, Fraud, Ledger, Notification), e só pela API pública deles.
 * Nenhum outro módulo conhece Payment.
 */
package com.techpix.payment;
