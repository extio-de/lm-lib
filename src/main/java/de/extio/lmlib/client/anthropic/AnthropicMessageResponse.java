package de.extio.lmlib.client.anthropic;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic Messages API response body.
 *
 * @param id the message id
 * @param type the response type ({@code message})
 * @param role the message role ({@code assistant})
 * @param model the model that generated the response
 * @param content the generated content blocks
 * @param stopReason why generation stopped
 * @param stopSequence the matched custom stop sequence, if any
 * @param usage the token usage information
 */
public record AnthropicMessageResponse(String id,
		String type,
		String role,
		String model,
		List<AnthropicContentBlock> content,
		@JsonProperty("stop_reason") String stopReason,
		@JsonProperty("stop_sequence") String stopSequence,
		AnthropicUsage usage) {

	public static final String STOP_REASON_END_TURN = "end_turn";
	public static final String STOP_REASON_MAX_TOKENS = "max_tokens";
	public static final String STOP_REASON_STOP_SEQUENCE = "stop_sequence";
	public static final String STOP_REASON_TOOL_USE = "tool_use";
	public static final String STOP_REASON_PAUSE_TURN = "pause_turn";
	public static final String STOP_REASON_REFUSAL = "refusal";
}
