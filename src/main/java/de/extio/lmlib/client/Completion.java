package de.extio.lmlib.client;

import java.time.Duration;

public final record Completion(String response, int requests, Duration duration, long inTokens, long outTokens, boolean cached) {
	
}
