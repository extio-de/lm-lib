package de.extio.lmlib.client.cached;

import java.time.OffsetDateTime;

import de.extio.lmlib.client.CompletionFinishReason;

public record CachedCompletion(String response, String reasoning, CompletionFinishReason finishReason, long inTokens, long cachedInTokens, long outTokens, long reasoningOutTokens, OffsetDateTime created) {
	
}
