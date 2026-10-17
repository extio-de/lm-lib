package de.extio.lmlib.client.anthropic;

/**
 * A single capability entry from the Anthropic {@code GET /v1/models} listing.
 * A missing {@code supported} flag is treated as supported so unknown or
 * partial capability information never fails a request.
 */
public record AnthropicCapabilitySupport(Boolean supported) {

	public boolean isSupported() {
		return this.supported == null || this.supported.booleanValue();
	}
}
