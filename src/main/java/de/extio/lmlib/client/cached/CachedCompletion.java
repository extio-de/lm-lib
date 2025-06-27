package de.extio.lmlib.client.cached;

import java.time.OffsetDateTime;

import de.extio.lmlib.client.CompletionFinishReason;

public record CachedCompletion(String response, CompletionFinishReason finishReason, long inTokens, long outTokens, OffsetDateTime created) {
	
}
