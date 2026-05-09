package de.extio.lmlib.client.ollama;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

final class OllamaMessage {

	private String role;

	private String content;

	private String thinking;

	private List<OllamaToolCall> toolCalls;

	private String toolCallId;

	private String toolName;

	@JsonProperty("role")
	public String getRole() {
		return this.role;
	}

	public void setRole(final String role) {
		this.role = role;
	}

	@JsonProperty("content")
	public String getContent() {
		return this.content;
	}

	public void setContent(final String content) {
		this.content = content;
	}

	@JsonProperty("thinking")
	public String getThinking() {
		return this.thinking;
	}

	public void setThinking(final String thinking) {
		this.thinking = thinking;
	}

	@JsonProperty("tool_calls")
	public List<OllamaToolCall> getToolCalls() {
		return this.toolCalls;
	}

	public void setToolCalls(final List<OllamaToolCall> toolCalls) {
		this.toolCalls = toolCalls;
	}

	@JsonProperty("tool_call_id")
	public String getToolCallId() {
		return this.toolCallId;
	}

	public void setToolCallId(final String toolCallId) {
		this.toolCallId = toolCallId;
	}

	@JsonProperty("tool_name")
	public String getToolName() {
		return this.toolName;
	}

	public void setToolName(final String toolName) {
		this.toolName = toolName;
	}
}