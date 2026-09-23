package com.techpix.fraud.internal.strangler;

import com.techpix.fraud.FraudMode;
import com.techpix.fraud.internal.FraudProperties;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Modo ativo do Strangler. Mutável em runtime: rollback é uma chamada HTTP, não um deploy. */
@Component
public class FraudModeConfig {

    private final AtomicReference<FraudMode> mode;

    public FraudModeConfig(FraudProperties properties) {
        this.mode = new AtomicReference<>(properties.mode());
    }

    public FraudMode mode() {
        return mode.get();
    }

    public void change(FraudMode newMode) {
        mode.set(newMode);
    }
}
