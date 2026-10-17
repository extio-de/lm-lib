package de.extio.lmlib.client.anthropic;

/**
 * A single input message of the Messages API.
 * <p>
 * {@code content} is either a plain string or a list of content blocks.
 *
 * @param role the message role ({@code user} or {@code assistant})
 * @param content string or list of content blocks
 */
public record AnthropicMessage(String role, Object content) {

	public static AnthropicMessage user(final String text) {
		return new AnthropicMessage("user", text);
	}

	public static AnthropicMessage userBlocks(final Object contentBlocks) {
		return new AnthropicMessage("user", contentBlocks);
	}

	public static AnthropicMessage assistant(final String text) {
		return new AnthropicMessage("assistant", text);
	}

	public static AnthropicMessage assistantBlocks(final Object contentBlocks) {
		return new AnthropicMessage("assistant", contentBlocks);
	}
}
