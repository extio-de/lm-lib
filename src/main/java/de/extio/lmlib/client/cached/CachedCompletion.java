package de.extio.lmlib.client.cached;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.ToolCall;

/**
 * Serializable cache entry for normalized lm-lib completions.
 */
public record CachedCompletion(String response, String reasoning, CompletionFinishReason finishReason, List<ToolCall> toolCalls, List<Map<String, Object>> outputItems, long inTokens, long cachedInTokens, long outTokens, long reasoningOutTokens, OffsetDateTime created) {

	public CachedCompletion {
		toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
		outputItems = outputItems == null ? List.of() : outputItems.stream().map(outputItem -> outputItem == null ? Map.<String, Object>of() : Map.copyOf(outputItem)).toList();
	}

}
