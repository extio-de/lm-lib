package de.extio.lmlib.client.oai;

import com.fasterxml.jackson.annotation.JsonValue;

import de.extio.lmlib.profile.ModelProfile;

/**
 * Optional dialect hook for OpenAI-compatible clients.
 * <p>
 * Applications can register this as a Spring bean to adapt request shaping for
 * providers that are not fully aligned with the current OpenAI Chat
 * Completions API surface.
 * <p>
 * If no bean is present, lm-lib uses the defaults defined on this interface.
 */
public interface OpenAiProviderDialect {

	/**
	 * Whether lm-lib should send the top-level {@code usage} request flag.
	 * <p>
	 * This is disabled by default because it is not part of the official OpenAI
	 * Chat Completions request schema and is mainly useful for llama-server style
	 * extensions.
	 *
	 * @param modelProfile the resolved model profile
	 * @return {@code true} if the request should include the top-level
	 *         {@code usage} flag
	 */
	default boolean sendUsage(final ModelProfile modelProfile) {
		return false;
	}

	/**
	 * Whether lm-lib should send a top-level {@code reasoning_effort} request
	 * field.
	 *
	 * @param modelProfile the resolved model profile
	 * @return {@code true} if {@code reasoning_effort} should be sent
	 */
	default boolean sendReasoning(final ModelProfile modelProfile) {
		return true;
	}

	/**
	 * Selects which token limit request parameter should be sent for chat
	 * completions.
	 * <p>
	 * {@code max_completion_tokens} is the current OpenAI recommendation, while
	 * older llama-server style implementations may still require
	 * {@code max_tokens}.
	 *
	 * @param modelProfile the resolved model profile
	 * @return the token limit parameter mode to use
	 */
	default ChatTokenLimitParameterMode chatTokenLimitParameterMode(final ModelProfile modelProfile) {
		return ChatTokenLimitParameterMode.MAX_COMPLETION_TOKENS;
	}

	/**
	 * Returns the reasoning effort to send for chat completions when reasoning is
	 * enabled.
	 *
	 * @param modelProfile the resolved model profile
	 * @return the reasoning effort value to send
	 */
	default ReasoningEffort reasoningEffort(final ModelProfile modelProfile) {
		return ReasoningEffort.MEDIUM;
	}

	/**
	 * Controls which chat token limit field lm-lib should send.
	 */
	enum ChatTokenLimitParameterMode {
		MAX_COMPLETION_TOKENS,
		MAX_TOKENS,
		BOTH
	}

	/**
	 * Official OpenAI reasoning effort values for chat completions.
	 */
	enum ReasoningEffort {
		NONE("none"),
		MINIMAL("minimal"),
		LOW("low"),
		MEDIUM("medium"),
		HIGH("high"),
		XHIGH("xhigh");

		private final String wireValue;

		ReasoningEffort(final String wireValue) {
			this.wireValue = wireValue;
		}

		@JsonValue
		public String getWireValue() {
			return this.wireValue;
		}
	}
}
