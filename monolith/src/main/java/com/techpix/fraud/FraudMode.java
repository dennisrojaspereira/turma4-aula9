package com.techpix.fraud;

/**
 * A Feature Flag do Strangler: quem decide sobre um pagamento.
 * <p>
 * LEGACY:   só o legado (in-process). Estado inicial e destino de qualquer rollback.
 * PARALLEL: o legado decide; o novo roda em shadow e os dois resultados são comparados.
 * NEW:      só o novo (Fraud Service).
 * <p>
 * Sem SaaS de feature flag: é um enum, um bean mutável e um endpoint administrativo.
 * Em Kubernetes, o valor inicial vem do ConfigMap; a troca ao vivo vem do endpoint.
 */
public enum FraudMode {
    LEGACY, PARALLEL, NEW
}
