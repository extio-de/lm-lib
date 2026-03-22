package de.extio.lmlib.client.oai.completion.chat;

import java.util.List;

import com.fasterxml.jackson.annotation.*;

final class ChatMessage {
	
	private String role;
	
	private String content;
	
	private String reasoningContent;

	private List<ChatToolCall> toolCalls;

	private String toolCallId;
	
	public ChatMessage() {
	}
	
	public ChatMessage(final String role, final String content, final String reasoningContent) {
		this.role = role;
		this.content = content;
		this.reasoningContent = reasoningContent;
	}
	
	@JsonProperty("role")
	public String getRole() {
		return role;
	}
	
	public void setRole(final String role) {
		this.role = role;
	}
	
	@JsonProperty("content")
	public String getContent() {
		return content;
	}
	
	public void setContent(final String content) {
		this.content = content;
	}
	
	@JsonProperty("reasoning_content")
	public String getReasoningContent() {
		return reasoningContent;
	}
	
	public void setReasoningContent(final String reasoningContent) {
		this.reasoningContent = reasoningContent;
	}

	@JsonProperty("tool_calls")
	public List<ChatToolCall> getToolCalls() {
		return this.toolCalls;
	}

	public void setToolCalls(final List<ChatToolCall> toolCalls) {
		this.toolCalls = toolCalls;
	}

	@JsonProperty("tool_call_id")
	public String getToolCallId() {
		return this.toolCallId;
	}

	public void setToolCallId(final String toolCallId) {
		this.toolCallId = toolCallId;
	}
}
