package de.extio.lmlib.client;

import java.math.BigDecimal;
import java.time.Duration;

public record CompletionStatistics(int requests, Duration duration, long inTokens, long cachedInTokens, long outTokens, long reasoningOutTokens, BigDecimal cost, boolean cached) {

}
