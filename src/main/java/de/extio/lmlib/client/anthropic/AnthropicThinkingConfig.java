package de.extio.lmlib.client.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic extended thinking configuration.
 *
 * @param type the thinking mode ({@code enabled} or {@code disabled})
 * @param budgetTokens the thinking budget in tokens (at least 1024, below max_tokens)
 */
public record AnthropicThinkingConfig(String type, @JsonProperty("budget_tokens") Integer budgetTokens) {

	public static AnthropicThinkingConfig enabled(final Integer budgetTokens) {
		return new AnthropicThinkingConfig("enabled", budgetTokens);
	}
}
