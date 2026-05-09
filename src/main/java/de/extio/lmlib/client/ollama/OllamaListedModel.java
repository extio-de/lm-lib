package de.extio.lmlib.client.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;

final class OllamaListedModel {

	private String name;

	private String model;

	@JsonProperty("name")
	public String getName() {
		return this.name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	@JsonProperty("model")
	public String getModel() {
		return this.model;
	}

	public void setModel(final String model) {
		this.model = model;
	}
}