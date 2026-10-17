package de.extio.lmlib.client.anthropic;

/**
 * Anthropic API error response body.
 *
 * @param type the response type ({@code error})
 * @param error the error detail
 */
public record AnthropicErrorResponse(String type, AnthropicError error) {

}
