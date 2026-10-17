package de.extio.lmlib.client.anthropic;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic client tool definition.
 *
 * @param name the tool name
 * @param description the tool description
 * @param inputSchema the JSON schema for the tool input
 */
public record AnthropicTool(String name, String description, @JsonProperty("input_schema") Map<String, Object> inputSchema) {

}
