package de.extio.lmlib.client.oai.completion.chat;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.extio.lmlib.client.oai.completion.AbstractCompletionRequest;

final class ChatCompletionRequest extends AbstractCompletionRequest {
	
	private List<ChatMessage> messages;
	
	private Integer maxCompletionTokens;

	private ChatCompletionsRequestReasoning reasoning;
	
	@JsonProperty("messages")
	public List<ChatMessage> getMessages() {
		return this.messages;
	}
	
	public void setMessages(final List<ChatMessage> messages) {
		this.messages = messages;
	}
	
	@JsonProperty("max_completion_tokens")
	public Integer getMaxCompletionTokens() {
		return this.maxCompletionTokens;
	}
	
	public void setMaxCompletionTokens(final Integer maxCompletionTokens) {
		this.maxCompletionTokens = maxCompletionTokens;
	}
	
	@JsonProperty("reasoning")
	public ChatCompletionsRequestReasoning getReasoning() {
		return this.reasoning;
	}

	public void setReasoning(final ChatCompletionsRequestReasoning reasoning) {
		this.reasoning = reasoning;
	}

	final static class ChatCompletionsRequestReasoning {

		private ChatCompletionsRequestReasoningEffort effort;

		private String summary;

		@JsonProperty("effort")
		public ChatCompletionsRequestReasoningEffort getEffort() {
			return this.effort;
		}

		public void setEffort(final ChatCompletionsRequestReasoningEffort effort) {
			this.effort = effort;
		}

		@JsonProperty("summary")
		public String getSummary() {
			return this.summary;
		}

		public void setSummary(final String summary) {
			this.summary = summary;
		}

	}

	static enum ChatCompletionsRequestReasoningEffort {
		minimal, low, medium, high
	}

	static enum ChatCompletionsRequestReasoningSummaryDetails {
		auto, concise, detailed
	}

}
