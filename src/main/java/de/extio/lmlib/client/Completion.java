package de.extio.lmlib.client;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

public final record Completion(String response, String reasoning, CompletionFinishReason finishReason, CompletionStatistics statistics, List<ToolCall> toolCalls) {

	public Completion {
		response = response == null ? "" : response;
		reasoning = reasoning == null ? "" : reasoning;
		finishReason = finishReason == null ? CompletionFinishReason.DONE : finishReason;
		statistics = statistics == null ? new CompletionStatistics(0, Duration.ZERO, 0, 0, 0, 0, BigDecimal.ZERO, false) : statistics;
		toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
	}

	public Completion(final String response, final String reasoning, final CompletionFinishReason finishReason, final CompletionStatistics statistics) {
		this(response, reasoning, finishReason, statistics, List.of());
	}

}
