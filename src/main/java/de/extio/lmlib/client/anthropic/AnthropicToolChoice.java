package de.extio.lmlib.client.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic tool choice configuration.
 *
 * @param type one of {@code auto}, {@code any}, {@code tool} or {@code none}
 * @param name the tool name for {@code type=tool}
 * @param disableParallelToolUse whether to disable parallel tool use
 */
public record AnthropicToolChoice(String type, String name, @JsonProperty("disable_parallel_tool_use") Boolean disableParallelToolUse) {

	public static AnthropicToolChoice auto() {
		return new AnthropicToolChoice("auto", null, null);
	}

	public static AnthropicToolChoice any() {
		return new AnthropicToolChoice("any", null, null);
	}

	public static AnthropicToolChoice none() {
		return new AnthropicToolChoice("none", null, null);
	}

	public static AnthropicToolChoice tool(final String name) {
		return new AnthropicToolChoice("tool", name, null);
	}
}
