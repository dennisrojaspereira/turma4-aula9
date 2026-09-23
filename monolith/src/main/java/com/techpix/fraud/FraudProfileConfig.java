package com.techpix.fraud;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Perfil ativo de Fraud. Mutável em runtime para que a aula troque "antes" e "depois" ao vivo. */
@Component
public class FraudProfileConfig {

    private final AtomicReference<FraudProfile> active;

    public FraudProfileConfig(FraudProperties properties) {
        this.active = new AtomicReference<>(properties.profile());
    }

    public FraudProfile active() {
        return active.get();
    }

    public void activate(FraudProfile profile) {
        active.set(profile);
    }
}
