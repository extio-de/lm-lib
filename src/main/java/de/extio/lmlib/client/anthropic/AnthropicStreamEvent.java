package de.extio.lmlib.client.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One server-sent event of a streamed Anthropic Messages API response.
 * <p>
 * Depending on {@code type}, only a subset of the fields is populated:
 * <ul>
 * <li>{@code message_start}: {@code message}</li>
 * <li>{@code content_block_start}: {@code index}, {@code contentBlock}</li>
 * <li>{@code content_block_delta}: {@code index}, {@code delta}</li>
 * <li>{@code content_block_stop}: {@code index}</li>
 * <li>{@code message_delta}: {@code delta}, {@code usage}</li>
 * <li>{@code error}: {@code error}</li>
 * </ul>
 *
 * @param type the event type
 * @param index the content block index
 * @param contentBlock the started content block
 * @param delta the incremental payload
 * @param message the initial message payload
 * @param usage the usage payload of {@code message_delta} events
 * @param error the error payload of {@code error} events
 */
public record AnthropicStreamEvent(String type,
		Integer index,
		@JsonProperty("content_block") AnthropicContentBlock contentBlock,
		AnthropicStreamEvent.Delta delta,
		AnthropicMessageResponse message,
		AnthropicUsage usage,
		AnthropicError error) {

	public static final String EVENT_MESSAGE_START = "message_start";
	public static final String EVENT_CONTENT_BLOCK_START = "content_block_start";
	public static final String EVENT_CONTENT_BLOCK_DELTA = "content_block_delta";
	public static final String EVENT_CONTENT_BLOCK_STOP = "content_block_stop";
	public static final String EVENT_MESSAGE_DELTA = "message_delta";
	public static final String EVENT_MESSAGE_STOP = "message_stop";
	public static final String EVENT_PING = "ping";
	public static final String EVENT_ERROR = "error";

	/**
	 * Incremental payload of delta events.
	 *
	 * @param type the delta type ({@code text_delta}, {@code thinking_delta},
	 *        {@code input_json_delta}, {@code signature_delta}; {@code message_delta}
	 *        events carry no type)
	 * @param text the appended text for {@code text_delta}
	 * @param thinking the appended thinking text for {@code thinking_delta}
	 * @param partialJson the appended tool input JSON fragment for {@code input_json_delta}
	 * @param signature the block signature for {@code signature_delta}
	 * @param stopReason the final stop reason for {@code message_delta}
	 * @param stopSequence the matched stop sequence for {@code message_delta}
	 */
	public record Delta(String type,
			String text,
			String thinking,
			@JsonProperty("partial_json") String partialJson,
			String signature,
			@JsonProperty("stop_reason") String stopReason,
			@JsonProperty("stop_sequence") String stopSequence) {

		public static final String DELTA_TYPE_TEXT = "text_delta";
		public static final String DELTA_TYPE_THINKING = "thinking_delta";
		public static final String DELTA_TYPE_INPUT_JSON = "input_json_delta";
		public static final String DELTA_TYPE_SIGNATURE = "signature_delta";
	}
}
