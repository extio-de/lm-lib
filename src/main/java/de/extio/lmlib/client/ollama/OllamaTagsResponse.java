package de.extio.lmlib.client.ollama;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

final class OllamaTagsResponse {

	private List<OllamaListedModel> models;

	@JsonProperty("models")
	public List<OllamaListedModel> getModels() {
		return this.models;
	}

	public void setModels(final List<OllamaListedModel> models) {
		this.models = models;
	}
}