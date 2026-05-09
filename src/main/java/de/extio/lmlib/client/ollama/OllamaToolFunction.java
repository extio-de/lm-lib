package de.extio.lmlib.client.ollama;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

final class OllamaToolFunction {

	private String name;

	private String description;

	private Map<String, Object> parameters;

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

	@JsonProperty("parameters")
	public Map<String, Object> getParameters() {
		return this.parameters;
	}

	public void setParameters(final Map<String, Object> parameters) {
		this.parameters = parameters;
	}
}