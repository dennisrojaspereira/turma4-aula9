package com.techpix.fraud.internal.strangler;

import static org.assertj.core.api.Assertions.assertThat;

import com.techpix.fraud.FraudMode;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.internal.FraudProperties;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CanaryRouterTest {

    private static CanaryRouter router(int percentage) {
        return new CanaryRouter(new FraudProperties(70, FraudProfile.SIMPLE, FraudMode.LEGACY, 0, 1,
                new FraudProperties.Remote("http://x", 1, 1, new FraudProperties.Retry(1, 0, 0, 0)), new FraudProperties.Parallel(1, 1, 1),
                new FraudProperties.Canary(percentage)));
    }

    @Test
    void zeroPercentRoutesNothingAndHundredRoutesEverything() {
        CanaryRouter none = router(0);
        CanaryRouter all = router(100);
        for (int i = 0; i < 1_000; i++) {
            UUID id = UUID.randomUUID();
            assertThat(none.routeToNew(id)).isFalse();
            assertThat(all.routeToNew(id)).isTrue();
        }
    }

    @Test
    void tenPercentRoutesRoughlyTenPercent() {
        CanaryRouter router = router(10);
        long routed = IntStream.range(0, 20_000).filter(i -> router.routeToNew(UUID.randomUUID())).count();
        assertThat(routed).isBetween(1_700L, 2_300L);
    }

    @Test
    void routingIsDeterministicPerPaymentId() {
        CanaryRouter router = router(50);
        UUID id = UUID.randomUUID();
        boolean first = router.routeToNew(id);
        for (int i = 0; i < 100; i++) {
            assertThat(router.routeToNew(id)).isEqualTo(first);
        }
    }

    @Test
    void percentageCanBeChangedAtRuntimeAndIsClamped() {
        CanaryRouter router = router(0);
        router.setPercentage(250);
        assertThat(router.percentage()).isEqualTo(100);
        router.setPercentage(-5);
        assertThat(router.percentage()).isZero();
    }
}
