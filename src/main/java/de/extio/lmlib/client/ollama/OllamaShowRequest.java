package de.extio.lmlib.client.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;

final class OllamaShowRequest {

	private String model;

	private Boolean verbose;

	@JsonProperty("model")
	public String getModel() {
		return this.model;
	}

	public void setModel(final String model) {
		this.model = model;
	}

	@JsonProperty("verbose")
	public Boolean getVerbose() {
		return this.verbose;
	}

	public void setVerbose(final Boolean verbose) {
		this.verbose = verbose;
	}
}