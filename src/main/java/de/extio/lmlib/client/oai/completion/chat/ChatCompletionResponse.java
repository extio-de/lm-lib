package de.extio.lmlib.client.oai.completion.chat;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.extio.lmlib.client.oai.completion.Usage;

final class ChatCompletionResponse {
	
	private String id;
	
	private String object;
	
	private Integer created;
	
	private List<ChatChoice> choices;
	
	private Usage usage;

	@JsonProperty("id")
	public String getId() {
		return this.id;
	}
	
	public void setId(final String id) {
		this.id = id;
	}
	
	@JsonProperty("object")
	public String getObject() {
		return this.object;
	}
	
	public void setObject(final String object) {
		this.object = object;
	}
	
	@JsonProperty("created")
	public Integer getCreated() {
		return this.created;
	}
	
	public void setCreated(final Integer created) {
		this.created = created;
	}
	
	@JsonProperty("choices")
	public List<ChatChoice> getChoices() {
		return this.choices;
	}
	
	public void setChoices(final List<ChatChoice> choices) {
		this.choices = choices;
	}
		
	@JsonProperty("usage")
	public Usage getUsage() {
		return usage;
	}

	public void setUsage(final Usage usage) {
		this.usage = usage;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("CompletionResponse [id=");
		builder.append(this.id);
		builder.append(", object=");
		builder.append(this.object);
		builder.append(", created=");
		builder.append(this.created);
		builder.append(", choices=");
		builder.append(this.choices);
		builder.append(", usage=");
		builder.append(this.usage);
		builder.append("]");
		return builder.toString();
	}
}
