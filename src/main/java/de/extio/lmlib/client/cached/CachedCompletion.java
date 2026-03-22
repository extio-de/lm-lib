package de.extio.lmlib.client.cached;

import java.time.OffsetDateTime;
import java.util.List;

import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.ToolCall;

public record CachedCompletion(String response, String reasoning, CompletionFinishReason finishReason, List<ToolCall> toolCalls, long inTokens, long cachedInTokens, long outTokens, long reasoningOutTokens, OffsetDateTime created) {
	
}
