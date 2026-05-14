package de.extio.lmlib.client;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Normalized result returned by lm-lib clients.
 *
 * @param response the assistant-visible text output
 * @param reasoning the separately exposed reasoning summary or reasoning text
 * @param finishReason why generation stopped
 * @param statistics token, duration, cache, and cost accounting for the request
 * @param toolCalls tool calls returned by the provider when the response stopped
 *        for tool execution
 * @param outputItems provider-specific structured output items that need to
 *        survive follow-up rounds, primarily for stateless OpenAI Responses tool
 *        loops
 */
public final record Completion(String response, String reasoning, CompletionFinishReason finishReason, CompletionStatistics statistics, List<ToolCall> toolCalls, List<Map<String, Object>> outputItems) {

	public Completion {
		response = response == null ? "" : response;
		reasoning = reasoning == null ? "" : reasoning;
		finishReason = finishReason == null ? CompletionFinishReason.DONE : finishReason;
		statistics = statistics == null ? new CompletionStatistics(0, Duration.ZERO, 0, 0, 0, 0, BigDecimal.ZERO, false) : statistics;
		toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
		outputItems = outputItems == null ? List.of() : outputItems.stream().map(outputItem -> outputItem == null ? Map.<String, Object>of() : Map.copyOf(outputItem)).toList();
	}

	/**
	 * Creates a completion without provider-specific output items.
	 */
	public Completion(final String response, final String reasoning, final CompletionFinishReason finishReason, final CompletionStatistics statistics, final List<ToolCall> toolCalls) {
		this(response, reasoning, finishReason, statistics, toolCalls, List.of());
	}

	/**
	 * Creates a plain text completion without tool calls or provider-specific
	 * output items.
	 */
	public Completion(final String response, final String reasoning, final CompletionFinishReason finishReason, final CompletionStatistics statistics) {
		this(response, reasoning, finishReason, statistics, List.of(), List.of());
	}

}
