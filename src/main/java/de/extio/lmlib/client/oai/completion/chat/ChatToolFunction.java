package de.extio.lmlib.client.oai.completion.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

final class ChatToolFunction {

	private String name;

	private String arguments;

	@JsonProperty("name")
	public String getName() {
		return this.name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	@JsonProperty("arguments")
	public String getArguments() {
		return this.arguments;
	}

	public void setArguments(final String arguments) {
		this.arguments = arguments;
	}

}