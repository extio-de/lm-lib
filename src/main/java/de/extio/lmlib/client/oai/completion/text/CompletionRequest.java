package de.extio.lmlib.client.oai.completion.text;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.extio.lmlib.client.oai.completion.AbstractCompletionRequest;

final class CompletionRequest extends AbstractCompletionRequest {
	
	private String prompt;
	
	@JsonProperty("prompt")
	public String getPrompt() {
		return this.prompt;
	}
	
	public void setPrompt(final String prompt) {
		this.prompt = prompt;
	}
}
