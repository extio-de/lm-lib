package de.extio.lmlib.client.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Capabilities of an Anthropic model as advertised by the
 * {@code GET /v1/models} endpoint. All fields are optional: providers and
 * compatible servers may omit some or all capabilities, in which case lm-lib
 * assumes the capability is available.
 * <p>
 * {@code toolUse} and {@code toolCalling} are not part of the official
 * capability set but are accepted for Anthropic-compatible servers that
 * advertise tool support through those keys.
 */
public record AnthropicModelCapabilities(
		AnthropicCapabilitySupport batch,
		AnthropicCapabilitySupport citations,
		@JsonProperty("code_execution") AnthropicCapabilitySupport codeExecution,
		@JsonProperty("image_input") AnthropicCapabilitySupport imageInput,
		@JsonProperty("pdf_input") AnthropicCapabilitySupport pdfInput,
		@JsonProperty("structured_outputs") AnthropicCapabilitySupport structuredOutputs,
		@JsonProperty("tool_use") AnthropicCapabilitySupport toolUse,
		@JsonProperty("tool_calling") AnthropicCapabilitySupport toolCalling,
		AnthropicThinkingCapability thinking) {

	public boolean isToolCallingSupported() {
		if (this.toolUse != null) {
			return this.toolUse.isSupported();
		}
		if (this.toolCalling != null) {
			return this.toolCalling.isSupported();
		}
		// The official capability set has no tool entry, so tool support cannot be
		// determined and the check is skipped.
		return true;
	}

	public boolean isExtendedThinkingSupported() {
		return this.thinking == null || this.thinking.isExtendedThinkingSupported();
	}
}
