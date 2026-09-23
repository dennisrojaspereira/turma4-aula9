package com.techpix.fraudservice.domain;

/** Porta: contas e dispositivos banidos. */
public interface Blacklist {

    boolean containsAccount(String accountId);

    boolean containsDevice(String deviceId);
}
