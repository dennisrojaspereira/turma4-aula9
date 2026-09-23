package com.techpix.fraud.internal.rules;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.internal.FraudRule;
import org.springframework.stereotype.Component;

/** Pagar para si mesmo é um padrão clássico de lavagem. Rejeição imediata. */
@Component
public class SamePartyRule implements FraudRule {

    @Override
    public String name() {
        return "same-party";
    }

    @Override
    public int evaluate(FraudCheck check) {
        return check.payerAccountId().equals(check.payeeAccountId()) ? 100 : 0;
    }
}
