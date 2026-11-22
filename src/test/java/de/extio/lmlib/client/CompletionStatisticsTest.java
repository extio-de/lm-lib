package de.extio.lmlib.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class CompletionStatisticsTest {

	@Test
	void dividesTokensByMilliseconds() {
		assertEquals(27, CompletionStatistics.tokensPerSecond(526, Duration.ofMillis(19_223)));
		assertEquals(1000, CompletionStatistics.tokensPerSecond(100, Duration.ofMillis(100)));
	}

	@Test
	void roundsToNearestTokenPerSecond() {
		assertEquals(67, CompletionStatistics.tokensPerSecond(200, Duration.ofMillis(3000)));
	}

	@Test
	void hasNoRateWithoutMeasuredDuration() {
		assertEquals(0, CompletionStatistics.tokensPerSecond(100, null));
		assertEquals(0, CompletionStatistics.tokensPerSecond(100, Duration.ZERO));
		assertEquals(0, CompletionStatistics.tokensPerSecond(100, Duration.ofMillis(-5)));
		assertEquals(0, CompletionStatistics.tokensPerSecond(100, Duration.ofNanos(500_000)));
		assertEquals(0, CompletionStatistics.tokensPerSecond(0, Duration.ofSeconds(1)));
	}

}
