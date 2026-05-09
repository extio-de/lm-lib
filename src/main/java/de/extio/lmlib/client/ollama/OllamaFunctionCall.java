package de.extio.lmlib.client.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;

final class OllamaFunctionCall {

	private String name;

	private String description;

	private Object arguments;

	@JsonProperty("name")
	public String getName() {
		return this.name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	@JsonProperty("description")
	public String getDescription() {
		return this.description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	@JsonProperty("arguments")
	public Object getArguments() {
		return this.arguments;
	}

	public void setArguments(final Object arguments) {
		this.arguments = arguments;
	}
}