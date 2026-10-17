package de.extio.lmlib.client.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A model entry from the Anthropic {@code GET /v1/models} listing.
 */
public record AnthropicModel(String id, @JsonProperty("display_name") String displayName, String type, String object, AnthropicModelCapabilities capabilities) {
}
