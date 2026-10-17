package de.extio.lmlib.client.anthropic;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic Messages API create-message request body.
 *
 * @param model the model name to generate the completion with
 * @param maxTokens the maximum number of tokens to generate (required by the API)
 * @param system the top-level system prompt (Anthropic has no system message role)
 * @param messages the conversational turns (user/assistant only)
 * @param temperature the sampling temperature
 * @param topP the nucleus sampling probability
 * @param stopSequences custom stop sequences
 * @param stream whether to stream the response via server-sent events
 * @param tools the tool definitions the model may use
 * @param toolChoice how the model should use the provided tools
 * @param thinking the extended thinking configuration
 */
public record AnthropicMessageRequest(String model,
		@JsonProperty("max_tokens") Integer maxTokens,
		String system,
		List<AnthropicMessage> messages,
		Double temperature,
		@JsonProperty("top_p") Double topP,
		@JsonProperty("stop_sequences") List<String> stopSequences,
		Boolean stream,
		List<AnthropicTool> tools,
		@JsonProperty("tool_choice") AnthropicToolChoice toolChoice,
		AnthropicThinkingConfig thinking) {

}
