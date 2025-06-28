package de.extio.lmlib.client.oai.completion.chat;

import java.util.List;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

final class ChatCompletionRequest {
	
	private String model;
	
	private List<ChatMessage> messages;
	
	private Integer maxTokens;
	
	private Integer maxCompletionTokens;
	
	private Double temperature;
	
	private Double topP;
	
	private Boolean stream;
	
	private ChatCompletionStreamOptions streamOptions;
	
	@JsonProperty("model")
	public String getModel() {
		return this.model;
	}
	
	public void setModel(final String model) {
		this.model = model;
	}
	
	@JsonProperty("messages")
	public List<ChatMessage> getMessages() {
		return this.messages;
	}
	
	public void setMessages(final List<ChatMessage> messages) {
		this.messages = messages;
	}
	
	@JsonProperty("max_tokens")
	public Integer getMaxTokens() {
		return this.maxTokens;
	}
	
	public void setMaxTokens(final Integer maxTokens) {
		this.maxTokens = maxTokens;
	}
	
	@JsonProperty("max_completion_tokens")
	public Integer getMaxCompletionTokens() {
		return this.maxCompletionTokens;
	}
	
	public void setMaxCompletionTokens(final Integer maxCompletionTokens) {
		this.maxCompletionTokens = maxCompletionTokens;
	}
	
	@JsonProperty("temperature")
	public Double getTemperature() {
		return this.temperature;
	}
	
	public void setTemperature(final Double temperature) {
		this.temperature = temperature;
	}
	
	@JsonProperty("top_p")
	public Double getTopP() {
		return this.topP;
	}
	
	public void setTopP(final Double topP) {
		this.topP = topP;
	}
	
	@JsonProperty("stream")
	public Boolean isStream() {
		return this.stream;
	}
	
	public void setStream(final Boolean stream) {
		this.stream = stream;
	}
	
	@JsonProperty("stream_options")
	public ChatCompletionStreamOptions getStreamOptions() {
		return streamOptions;
	}
	
	public void setStreamOptions(ChatCompletionStreamOptions streamOptions) {
		this.streamOptions = streamOptions;
	}
	
}
