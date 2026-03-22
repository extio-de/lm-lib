package de.extio.lmlib.client.oai.completion.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

final class ChatToolCall {

	private Integer index;

	private String id;

	private String type;

	private ChatToolFunction function;

	@JsonProperty("index")
	public Integer getIndex() {
		return this.index;
	}

	public void setIndex(final Integer index) {
		this.index = index;
	}

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
	public ChatToolFunction getFunction() {
		return this.function;
	}

	public void setFunction(final ChatToolFunction function) {
		this.function = function;
	}

}