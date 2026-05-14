package de.extio.lmlib.client.oai.responses;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

import de.extio.lmlib.profile.ModelProfile;

/**
 * Optional request-shaping hook for the standalone OpenAI Responses client.
 * <p>
 * Applications can register this as a Spring bean to opt into Responses-specific
 * request fields without affecting the chat-completions clients.
 */
public interface OpenAiResponsesApiDialect {

	/**
	 * Include value that asks the Responses API to return encrypted reasoning
	 * payloads. This is required when reasoning items should survive stateless
	 * tool-call loops.
	 */
	String INCLUDE_REASONING_ENCRYPTED_CONTENT = "reasoning.encrypted_content";

	/**
	 * Controls whether OpenAI should persist the server-side response state for
	 * later retrieval or follow-up requests.
	 * <p>
	 * lm-lib defaults this to {@code false} because the Responses client replays the
	 * full conversation statelessly.
	 *
	 * @param modelProfile the resolved model profile for the request
	 * @return {@code true} to store responses server-side, otherwise {@code false}
	 */
	default boolean store(final ModelProfile modelProfile) {
		return false;
	}

	/**
	 * Controls the optional {@code background} request flag.
	 * <p>
	 * When enabled, the provider may continue processing the response after the
	 * initial request has been accepted.
	 *
	 * @param modelProfile the resolved model profile for the request
	 * @return {@code true} or {@code false} to send the flag, or {@code null} to
	 *         omit it
	 */
	default Boolean background(final ModelProfile modelProfile) {
		return null;
	}

	/**
	 * Additional output sections to request via the Responses {@code include}
	 * field.
	 * <p>
	 * Use this for optional payloads such as encrypted reasoning, tool results, or
	 * output logprobs.
	 *
	 * @param modelProfile the resolved model profile for the request
	 * @return includable field names to send with the request
	 */
	default List<String> include(final ModelProfile modelProfile) {
		return List.of();
	}

	/**
	 * Caps the number of built-in tool calls the Responses API may execute during a
	 * single request.
	 *
	 * @param modelProfile the resolved model profile for the request
	 * @return the maximum number of built-in tool calls, or {@code null} to omit
	 *         the field
	 */
	default Integer maxToolCalls(final ModelProfile modelProfile) {
		return null;
	}

	/**
	 * Supplies request metadata that OpenAI stores alongside the generated
	 * response.
	 * <p>
	 * This is useful for provider-side filtering, trace correlation, or dashboard
	 * inspection.
	 *
	 * @param modelProfile the resolved model profile for the request
	 * @return metadata entries to attach to the request
	 */
	default Map<String, String> metadata(final ModelProfile modelProfile) {
		return Map.of();
	}

	/**
	 * Sets the optional {@code prompt_cache_key} used by OpenAI prompt caching.
	 *
	 * @param modelProfile the resolved model profile for the request
	 * @return the cache key, or {@code null} to omit it
	 */
	default String promptCacheKey(final ModelProfile modelProfile) {
		return null;
	}

	/**
	 * Selects how long OpenAI may retain cached prompt prefixes for this request.
	 *
	 * @param modelProfile the resolved model profile for the request
	 * @return the prompt-cache retention policy, or {@code null} to omit it
	 */
	default PromptCacheRetention promptCacheRetention(final ModelProfile modelProfile) {
		return null;
	}

	/**
	 * Configures the Responses {@code reasoning} object for reasoning-capable
	 * models.
	 * <p>
	 * This controls effort level and optional summary generation settings.
	 *
	 * @param modelProfile the resolved model profile for the request
	 * @return the reasoning configuration to send, or {@code null} to omit it
	 */
	default ResponsesReasoning reasoning(final ModelProfile modelProfile) {
		return new ResponsesReasoning(ReasoningEffort.MEDIUM, null, null);
	}

	/**
	 * Supplies the optional {@code safety_identifier} field.
	 * <p>
	 * This should be a stable, non-identifying user or tenant token that helps the
	 * provider detect abuse patterns.
	 *
	 * @param modelProfile the resolved model profile for the request
	 * @return the safety identifier, or {@code null} to omit it
	 */
	default String safetyIdentifier(final ModelProfile modelProfile) {
		return null;
	}

	/**
	 * Selects the OpenAI processing tier for the request when the provider supports
	 * tiered serving.
	 *
	 * @param modelProfile the resolved model profile for the request
	 * @return the requested service tier, or {@code null} to omit it
	 */
	default ServiceTier serviceTier(final ModelProfile modelProfile) {
		return null;
	}

	/**
	 * Configures the optional Responses {@code stream_options} object.
	 *
	 * @param modelProfile the resolved model profile for the request
	 * @return stream options, or {@code null} to omit them
	 */
	default ResponsesStreamOptions streamOptions(final ModelProfile modelProfile) {
		return null;
	}

	/**
	 * Configures the optional Responses {@code text} object.
	 * <p>
	 * This can be used to influence text-only output shaping such as verbosity.
	 *
	 * @param modelProfile the resolved model profile for the request
	 * @return text output options, or {@code null} to omit them
	 */
	default ResponsesText text(final ModelProfile modelProfile) {
		return null;
	}

	/**
	 * Requests token-level log probability data for text outputs.
	 *
	 * @param modelProfile the resolved model profile for the request
	 * @return the number of top logprobs to request, or {@code null} to omit the
	 *         field
	 */
	default Integer topLogprobs(final ModelProfile modelProfile) {
		return null;
	}

	/**
	 * Selects how the provider should react when the input exceeds the model's
	 * context window.
	 *
	 * @param modelProfile the resolved model profile for the request
	 * @return the truncation mode, or {@code null} to omit it
	 */
	default Truncation truncation(final ModelProfile modelProfile) {
		return null;
	}

	/**
	 * Request payload for the Responses {@code reasoning} object.
	 *
	 * @param effort the reasoning budget requested from the model
	 * @param generateSummary optional instruction for whether and how the provider
	 *        should generate reasoning summaries
	 * @param summary optional summary detail level for reasoning output
	 */
	record ResponsesReasoning(ReasoningEffort effort, ReasoningSummaryGenerateMode generateSummary, ReasoningSummary summary) {
	}

	/**
	 * Request payload for the Responses {@code stream_options} object.
	 *
	 * @param includeObfuscation whether streaming responses should include the
	 *        provider's obfuscation metadata when available
	 */
	record ResponsesStreamOptions(Boolean includeObfuscation) {
	}

	/**
	 * Request payload for the Responses {@code text} object.
	 *
	 * @param verbosity desired verbosity level for text output
	 */
	record ResponsesText(TextVerbosity verbosity) {
	}

	/**
	 * Retention policy for prompt caching.
	 */
	enum PromptCacheRetention {
		/** Retain cached prompt prefixes in volatile memory only. */
		IN_MEMORY("in_memory"),
		/** Retain cached prompt prefixes for up to 24 hours. */
		DAY_24H("24h");

		private final String wireValue;

		PromptCacheRetention(final String wireValue) {
			this.wireValue = wireValue;
		}

		@JsonValue
		public String getWireValue() {
			return this.wireValue;
		}
	}

	/**
	 * Reasoning effort levels supported by the Responses API.
	 */
	enum ReasoningEffort {
		/** Disable extra reasoning tokens when the model supports that mode. */
		NONE("none"),
		/** Request the smallest possible reasoning budget. */
		MINIMAL("minimal"),
		/** Request a low reasoning budget. */
		LOW("low"),
		/** Request the default medium reasoning budget. */
		MEDIUM("medium"),
		/** Request a high reasoning budget. */
		HIGH("high"),
		/** Request the highest extended reasoning budget. */
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

	/**
	 * Summary detail levels for returned reasoning summaries.
	 */
	enum ReasoningSummary {
		/** Let the provider pick the summary detail level automatically. */
		AUTO("auto"),
		/** Request a short reasoning summary. */
		CONCISE("concise"),
		/** Request a more detailed reasoning summary. */
		DETAILED("detailed");

		private final String wireValue;

		ReasoningSummary(final String wireValue) {
			this.wireValue = wireValue;
		}

		@JsonValue
		public String getWireValue() {
			return this.wireValue;
		}
	}

	/**
	 * Modes for asking the provider to generate reasoning summaries.
	 */
	enum ReasoningSummaryGenerateMode {
		/** Let the provider decide whether and how to generate a summary. */
		AUTO("auto"),
		/** Generate a short reasoning summary. */
		CONCISE("concise"),
		/** Generate a detailed reasoning summary. */
		DETAILED("detailed"),
		/** Do not generate a reasoning summary. */
		NONE("none");

		private final String wireValue;

		ReasoningSummaryGenerateMode(final String wireValue) {
			this.wireValue = wireValue;
		}

		@JsonValue
		public String getWireValue() {
			return this.wireValue;
		}
	}

	/**
	 * OpenAI processing tiers supported by the Responses API.
	 */
	enum ServiceTier {
		/** Let the provider choose the service tier automatically. */
		AUTO("auto"),
		/** Use the standard service tier. */
		DEFAULT("default"),
		/** Use flex processing when available. */
		FLEX("flex"),
		/** Use priority processing when available. */
		PRIORITY("priority");

		private final String wireValue;

		ServiceTier(final String wireValue) {
			this.wireValue = wireValue;
		}

		@JsonValue
		public String getWireValue() {
			return this.wireValue;
		}
	}

	/**
	 * Verbosity levels for text output.
	 */
	enum TextVerbosity {
		/** Prefer shorter text outputs. */
		LOW("low"),
		/** Prefer balanced text outputs. */
		MEDIUM("medium"),
		/** Prefer more detailed text outputs. */
		HIGH("high");

		private final String wireValue;

		TextVerbosity(final String wireValue) {
			this.wireValue = wireValue;
		}

		@JsonValue
		public String getWireValue() {
			return this.wireValue;
		}
	}

	/**
	 * Input truncation strategies for oversized requests.
	 */
	enum Truncation {
		/** Allow the provider to drop older context automatically. */
		AUTO("auto"),
		/** Reject oversized requests instead of truncating them. */
		DISABLED("disabled");

		private final String wireValue;

		Truncation(final String wireValue) {
			this.wireValue = wireValue;
		}

		@JsonValue
		public String getWireValue() {
			return this.wireValue;
		}
	}
}
