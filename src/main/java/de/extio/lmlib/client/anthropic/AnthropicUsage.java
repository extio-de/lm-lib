package de.extio.lmlib.client.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic token usage information.
 * <p>
 * {@code inputTokens} excludes cached token counts. Thinking tokens are billed as
 * part of {@code outputTokens}.
 *
 * @param inputTokens the number of non-cached input tokens
 * @param outputTokens the number of output tokens (including thinking)
 * @param cacheCreationInputTokens the number of input tokens written to the prompt cache
 * @param cacheReadInputTokens the number of input tokens read from the prompt cache
 */
public record AnthropicUsage(@JsonProperty("input_tokens") Integer inputTokens,
		@JsonProperty("output_tokens") Integer outputTokens,
		@JsonProperty("cache_creation_input_tokens") Integer cacheCreationInputTokens,
		@JsonProperty("cache_read_input_tokens") Integer cacheReadInputTokens) {

	/**
	 * Merges a later usage snapshot onto this one, preferring non-null values from
	 * the later snapshot.
	 */
	public AnthropicUsage mergedWith(final AnthropicUsage later) {
		if (later == null) {
			return this;
		}
		return new AnthropicUsage(
				later.inputTokens() != null ? later.inputTokens() : this.inputTokens,
				later.outputTokens() != null ? later.outputTokens() : this.outputTokens,
				later.cacheCreationInputTokens() != null ? later.cacheCreationInputTokens() : this.cacheCreationInputTokens,
				later.cacheReadInputTokens() != null ? later.cacheReadInputTokens() : this.cacheReadInputTokens);
	}
}
