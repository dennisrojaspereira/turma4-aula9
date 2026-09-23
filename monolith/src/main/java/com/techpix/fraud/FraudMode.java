package com.techpix.fraud;

/**
 * Quem decide: o Fraud legado (in-process) ou o Fraud Service (remoto).
 * <p>
 * LEGACY: só o legado. É o estado inicial e o destino de qualquer rollback.
 * NEW: só o remoto.
 * <p>
 * Outros modos (PARALLEL, CANARY) entram quando houver motivo para eles.
 */
public enum FraudMode {
    LEGACY, NEW
}
