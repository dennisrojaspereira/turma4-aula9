package com.techpix.fraud;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "techpix.fraud")
public record FraudProperties(int rejectThreshold) {
}
