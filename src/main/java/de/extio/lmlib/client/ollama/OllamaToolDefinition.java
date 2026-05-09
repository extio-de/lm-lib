package de.extio.lmlib.client.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;

final class OllamaToolDefinition {

	private String type;

	private OllamaToolFunction function;

	@JsonProperty("type")
	public String getType() {
		return this.type;
	}

	public void setType(final String type) {
		this.type = type;
	}

	@JsonProperty("function")
	public OllamaToolFunction getFunction() {
		return this.function;
	}

	public void setFunction(final OllamaToolFunction function) {
		this.function = function;
	}
}