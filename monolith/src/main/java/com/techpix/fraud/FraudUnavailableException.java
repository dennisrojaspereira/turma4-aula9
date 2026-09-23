package com.techpix.fraud;

import com.techpix.shared.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Fraud não conseguiu decidir. Antes da extração esta exceção não existia: uma chamada de método
 * ou respondia ou derrubava o processo. Agora existe um terceiro estado: "não sei, tente depois".
 */
public class FraudUnavailableException extends DomainException {

    public FraudUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
        initCause(cause);
    }
}
