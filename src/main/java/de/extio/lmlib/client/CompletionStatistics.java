package de.extio.lmlib.client;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Token, duration, throughput, cache, and cost accounting for a completion.
 *
 * @param requests the number of provider requests covered by this snapshot
 * @param duration the request duration
 * @param inTokens the input tokens that were not read from a prompt cache
 * @param cachedInTokens the input tokens read from a prompt cache
 * @param outTokens the output tokens without reasoning tokens
 * @param reasoningOutTokens the reasoning or thinking output tokens
 * @param prefillTps input tokens per second between request start and first streamed token,
 *        taken from the response when the provider reports it, zero when unknown
 * @param outTps output and reasoning tokens per second between first streamed token and end of
 *        response, taken from the response when the provider reports it, zero when unknown
 * @param requestTps output and reasoning tokens per second over the whole request duration
 * @param cost the accounting cost of the completion
 * @param cached whether the completion was served from the local cache
 */
public record CompletionStatistics(int requests, Duration duration, long inTokens, long cachedInTokens, long outTokens, long reasoningOutTokens, int prefillTps, int outTps, int requestTps, BigDecimal cost, boolean cached) {

	/**
	 * Calculates a tokens-per-second rate with millisecond precision.
	 *
	 * @param tokens the tokens to divide over the duration
	 * @param duration the measured duration
	 * @return the rounded tokens per second, zero when tokens or duration are missing
	 */
	public static int tokensPerSecond(final long tokens, final Duration duration) {
		final var millis = duration == null ? 0L : duration.toMillis();
		if (tokens <= 0 || millis <= 0) {
			return 0;
		}
		return (int) Math.round((double) tokens * 1_000.0 / (double) millis);
	}

}
