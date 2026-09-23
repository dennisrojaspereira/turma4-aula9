package com.techpix.fraud;

/**
 * A Feature Flag do Strangler: quem decide sobre um pagamento.
 * <p>
 * LEGACY:   só o legado (in-process). Estado inicial e destino de qualquer rollback.
 * PARALLEL: o legado decide; o novo roda em shadow e os dois resultados são comparados.
 * CANARY:   uma fração dos pagamentos (por hash do id) é decidida pelo novo; o resto, pelo legado.
 * NEW:      só o novo (Fraud Service).
 * <p>
 * Sem SaaS de feature flag: é um enum, um bean mutável e um endpoint administrativo.
 * Em Kubernetes, o valor inicial vem do ConfigMap; a troca ao vivo vem do endpoint.
 */
public enum FraudMode {
    LEGACY, PARALLEL, CANARY, NEW
}
