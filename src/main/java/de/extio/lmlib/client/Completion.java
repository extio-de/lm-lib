package de.extio.lmlib.client;

import java.util.List;

public final record Completion(String response, String reasoning, CompletionFinishReason finishReason, CompletionStatistics statistics, List<ToolCall> toolCalls) {

	public Completion {
		toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
	}

	public Completion(final String response, final String reasoning, final CompletionFinishReason finishReason, final CompletionStatistics statistics) {
		this(response, reasoning, finishReason, statistics, List.of());
	}

}
