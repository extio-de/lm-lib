package de.extio.lmlib.client.cached;

import java.time.OffsetDateTime;

public record CachedCompletion(String response, long inTokens, long outTokens, OffsetDateTime created) {
	
}
