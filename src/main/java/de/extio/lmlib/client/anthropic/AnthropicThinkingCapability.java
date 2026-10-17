package de.extio.lmlib.client.anthropic;

/**
 * Thinking capability of an Anthropic model, including the supported thinking
 * type configurations.
 */
public record AnthropicThinkingCapability(Boolean supported, ThinkingTypes types) {

	public boolean isSupported() {
		return this.supported == null || this.supported.booleanValue();
	}

	public boolean isExtendedThinkingSupported() {
		if (!this.isSupported()) {
			return false;
		}
		return this.types == null || this.types.enabled() == null || this.types.enabled().isSupported();
	}

	public record ThinkingTypes(AnthropicCapabilitySupport enabled, AnthropicCapabilitySupport disabled, AnthropicCapabilitySupport adaptive) {
	}
}
