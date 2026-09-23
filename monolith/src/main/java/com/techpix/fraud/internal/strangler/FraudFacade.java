package com.techpix.fraud.internal.strangler;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudEvaluator;
import com.techpix.fraud.FraudResult;
import com.techpix.fraud.internal.FraudService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Strangler Fig: a fachada que Payment enxerga.
 * <pre>
 * Payment
 *    |
 *    v
 * FraudFacade
 *    |
 *    +------> LegacyFraud   (FraudService, in-process)
 *    |
 *    +------> NewFraud      (RemoteFraudEvaluator, HTTP -> Fraud Service)
 * </pre>
 * Payment não sabe qual dos dois respondeu. A escolha é configuração, alterável em runtime.
 * O legado não é removido: convive com o novo até o novo provar que é equivalente e seguro.
 */
@Component
@Primary
public class FraudFacade implements FraudEvaluator {

    private final FraudService legacy;
    private final RemoteFraudEvaluator remote;
    private final FraudModeConfig mode;

    public FraudFacade(FraudService legacy, RemoteFraudEvaluator remote, FraudModeConfig mode) {
        this.legacy = legacy;
        this.remote = remote;
        this.mode = mode;
    }

    @Override
    public FraudResult evaluate(FraudCheck check) {
        return switch (mode.mode()) {
            case LEGACY -> legacy.evaluate(check);
            case NEW -> remote.evaluate(check);
        };
    }
}
