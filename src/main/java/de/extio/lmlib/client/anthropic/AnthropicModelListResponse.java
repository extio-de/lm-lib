package de.extio.lmlib.client.anthropic;

import java.util.List;

/**
 * Response of the Anthropic {@code GET /v1/models} endpoint.
 */
public record AnthropicModelListResponse(List<AnthropicModel> data) {
}
