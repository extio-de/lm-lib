package de.extio.lmlib.client.anthropic;

/**
 * Anthropic API error detail.
 *
 * @param type the error type (e.g. {@code invalid_request_error})
 * @param message the human-readable error message
 */
public record AnthropicError(String type, String message) {

}
