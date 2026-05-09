package de.extio.lmlib.client.ollama;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

final class OllamaShowResponse {

	private List<String> capabilities;

	@JsonProperty("capabilities")
	public List<String> getCapabilities() {
		return this.capabilities;
	}

	public void setCapabilities(final List<String> capabilities) {
		this.capabilities = capabilities;
	}
}