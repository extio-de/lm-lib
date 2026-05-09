package de.extio.lmlib.client.ollama;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

final class OllamaChatRequest {

	private String model;

	private List<OllamaMessage> messages;

	private List<OllamaToolDefinition> tools;

	private Map<String, Object> options;

	private Boolean stream;

	private Object think;

	@JsonProperty("model")
	public String getModel() {
		return this.model;
	}

	public void setModel(final String model) {
		this.model = model;
	}

	@JsonProperty("messages")
	public List<OllamaMessage> getMessages() {
		return this.messages;
	}

	public void setMessages(final List<OllamaMessage> messages) {
		this.messages = messages;
	}

	@JsonProperty("tools")
	public List<OllamaToolDefinition> getTools() {
		return this.tools;
	}

	public void setTools(final List<OllamaToolDefinition> tools) {
		this.tools = tools;
	}

	@JsonProperty("options")
	public Map<String, Object> getOptions() {
		return this.options;
	}

	public void setOptions(final Map<String, Object> options) {
		this.options = options;
	}

	@JsonProperty("stream")
	public Boolean getStream() {
		return this.stream;
	}

	public void setStream(final Boolean stream) {
		this.stream = stream;
	}

	@JsonProperty("think")
	public Object getThink() {
		return this.think;
	}

	public void setThink(final Object think) {
		this.think = think;
	}
}