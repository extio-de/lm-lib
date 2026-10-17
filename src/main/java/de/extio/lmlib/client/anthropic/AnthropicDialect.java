package de.extio.lmlib.client.anthropic;

import de.extio.lmlib.profile.ModelProfile;

/**
 * Optional dialect hook for the Anthropic Messages API client.
 * <p>
 * Applications can register this as a Spring bean to adapt request shaping for
 * Anthropic-compatible providers that deviate from the official API surface.
 * <p>
 * If no bean is present, lm-lib uses the defaults defined on this interface.
 */
public interface AnthropicDialect {

	/**
	 * The minimum extended thinking budget accepted by the Anthropic API.
	 */
	int MIN_THINKING_BUDGET_TOKENS = 1024;

	/**
	 * Returns the {@code anthropic-version} request header value.
	 *
	 * @param modelProfile the resolved model profile
	 * @return the API version header value
	 */
	default String anthropicVersion(final ModelProfile modelProfile) {
		return "2023-06-01";
	}

	/**
	 * Returns the extended thinking budget in tokens.
	 * <p>
	 * Thinking is disabled by default. When a budget is returned, the request
	 * includes {@code thinking: {"type": "enabled", "budget_tokens": N}} with the
	 * budget clamped to the API limits (at least {@value #MIN_THINKING_BUDGET_TOKENS}
	 * and below {@code max_tokens}). Thinking blocks returned by the model are
	 * exposed as reasoning and replayed unchanged in follow-up tool-call rounds.
	 *
	 * @param modelProfile the resolved model profile
	 * @return the thinking budget in tokens, or {@code null} to disable thinking
	 */
	default Integer thinkingBudgetTokens(final ModelProfile modelProfile) {
		return null;
	}
}
