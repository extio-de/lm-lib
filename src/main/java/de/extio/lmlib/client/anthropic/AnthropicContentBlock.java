package de.extio.lmlib.client.anthropic;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic content block used for both request building and response parsing.
 * <p>
 * Depending on {@code type}, only a subset of the fields is used:
 * <ul>
 * <li>{@code text}: {@code text}</li>
 * <li>{@code tool_use}: {@code id}, {@code name}, {@code input}</li>
 * <li>{@code tool_result}: {@code toolUseId}, {@code content}, {@code isError}</li>
 * <li>{@code thinking}: {@code thinking}, {@code signature}</li>
 * <li>{@code redacted_thinking}: {@code data}</li>
 * </ul>
 */
public record AnthropicContentBlock(String type,
		String text,
		String id,
		String name,
		Map<String, Object> input,
		@JsonProperty("tool_use_id") String toolUseId,
		Object content,
		@JsonProperty("is_error") Boolean isError,
		String thinking,
		String signature,
		String data) {

	public static AnthropicContentBlock text(final String text) {
		return new AnthropicContentBlock("text", text, null, null, null, null, null, null, null, null, null);
	}

	public static AnthropicContentBlock toolUse(final String id, final String name, final Map<String, Object> input) {
		return new AnthropicContentBlock("tool_use", null, id, name, input, null, null, null, null, null, null);
	}

	public static AnthropicContentBlock toolResult(final String toolUseId, final String content) {
		return new AnthropicContentBlock("tool_result", null, null, null, null, toolUseId, content, null, null, null, null);
	}

	public static AnthropicContentBlock thinking(final String thinking, final String signature) {
		return new AnthropicContentBlock("thinking", null, null, null, null, null, null, null, thinking, signature, null);
	}

	public static AnthropicContentBlock redactedThinking(final String data) {
		return new AnthropicContentBlock("redacted_thinking", null, null, null, null, null, null, null, null, null, data);
	}
}
