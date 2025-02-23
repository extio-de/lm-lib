package de.extio.lmlib.client;

import java.math.BigDecimal;
import java.time.Duration;

public final record Completion(String response, int requests, Duration duration, long inTokens, long outTokens, BigDecimal cost, boolean cached) {
	
}
