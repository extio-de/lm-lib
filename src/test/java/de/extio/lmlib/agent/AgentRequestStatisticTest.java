package de.extio.lmlib.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.CompletionStatistics;

class AgentRequestStatisticTest {

	@Test
	void averagesThroughputOverTheReconstructedPhaseDurations() {
		final var statistic = AgentRequestStatistic.create();
		statistic.add(this.createCompletion(100, 100, 100, 100, 50));
		statistic.add(this.createCompletion(100, 100, 50, 50, 50));

		// 200 tokens over 1s + 2s
		assertEquals(67, statistic.getPrefillTps());
		assertEquals(67, statistic.getOutTps());
		// 200 tokens over 2s + 2s
		assertEquals(50, statistic.getRequestTps());
	}

	@Test
	void unknownThroughputIsNotWeighed() {
		final var statistic = AgentRequestStatistic.create();
		statistic.add(this.createCompletion(100, 100, 100, 100, 50));
		statistic.add(this.createCompletion(1000, 1000, 0, 0, 0));

		assertEquals(100, statistic.getPrefillTps());
		assertEquals(100, statistic.getOutTps());
		assertEquals(50, statistic.getRequestTps());
	}

	@Test
	void cachedCompletionsDoNotContributeToThroughput() {
		final var statistic = AgentRequestStatistic.create();
		statistic.add(this.createCompletion(100, 100, 100, 100, 50));
		statistic.add(this.createCompletion(1000, 1000, 0, 0, 0, true));

		assertEquals(1, statistic.getCachedPrompts().get());
		assertEquals(100, statistic.getPrefillTps());
		assertEquals(100, statistic.getOutTps());
		assertEquals(50, statistic.getRequestTps());
	}

	@Test
	void mergesThroughputSamplesOfOtherStatistics() {
		final var statistic = AgentRequestStatistic.create();
		statistic.add(this.createCompletion(100, 100, 100, 100, 50));
		final var other = AgentRequestStatistic.create();
		other.add(this.createCompletion(100, 100, 50, 50, 50));

		statistic.add(other);

		assertEquals(200, statistic.getInTokens().get());
		assertEquals(67, statistic.getPrefillTps());
		assertEquals(67, statistic.getOutTps());
		assertEquals(50, statistic.getRequestTps());
	}

	private Completion createCompletion(final long inTokens, final long outTokens, final int prefillTps, final int outTps, final int requestTps) {
		return this.createCompletion(inTokens, outTokens, prefillTps, outTps, requestTps, false);
	}

	private Completion createCompletion(final long inTokens, final long outTokens, final int prefillTps, final int outTps, final int requestTps, final boolean cached) {
		return new Completion("response", null, CompletionFinishReason.DONE, new CompletionStatistics(1, Duration.ofSeconds(2), inTokens, 0, outTokens, 0, prefillTps, outTps, requestTps, BigDecimal.ZERO, cached));
	}

}
