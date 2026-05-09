package de.extio.lmlib.client.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;

final class OllamaToolCall {

	private String id;

	private String type;

	private OllamaFunctionCall function;

	@JsonProperty("id")
	public String getId() {
		return this.id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	@JsonProperty("type")
	public String getType() {
		return this.type;
	}

	public void setType(final String type) {
		this.type = type;
	}

	@JsonProperty("function")
	public OllamaFunctionCall getFunction() {
		return this.function;
	}

	public void setFunction(final OllamaFunctionCall function) {
		this.function = function;
	}
}