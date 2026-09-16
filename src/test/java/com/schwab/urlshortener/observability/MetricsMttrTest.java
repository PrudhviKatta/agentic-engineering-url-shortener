package com.schwab.urlshortener.observability;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsMttrTest {

    private static Instant time(long secondsOffset) {
        return Instant.parse("2026-01-01T00:00:00Z").plusSeconds(secondsOffset);
    }

    @Test
    void averagesOnlyRecoveredEventsAndReportsExcludedPopulationsSeparately() {
        MttrReport report = MetricsService.computeMttr(List.of(
                new RecoveryEvent(time(0), time(1), time(11), true, "retry"),
                new RecoveryEvent(time(20), time(21), time(41), true, "compensation"),
                new RecoveryEvent(time(50), time(51), null, false, "none"),
                new RecoveryEvent(time(60), time(70), null, true, "retry")));

        assertThat(report.mttrSeconds()).isEqualTo(15.0);
        assertThat(report.recoveredCount()).isEqualTo(2);
        assertThat(report.unrecoveredCount()).isEqualTo(1);
        assertThat(report.excludedCount()).isEqualTo(1);
        assertThat(report.populationNote())
                .containsIgnoringCase("demonstration")
                .containsIgnoringCase("not production");
    }

    @Test
    void returnsNullRatherThanZeroWhenNothingRecovered() {
        MttrReport report = MetricsService.computeMttr(List.of(
                new RecoveryEvent(time(0), time(1), null, false, "none")));

        assertThat(report.mttrSeconds()).isNull();
        assertThat(report.recoveredCount()).isZero();
        assertThat(report.unrecoveredCount()).isEqualTo(1);
    }
}
