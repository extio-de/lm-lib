package de.extio.lmlib.client.oai.completion;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class AbstractCompletionResponse {
	
	protected String id;
	
	protected String object;
	
	protected Integer created;
	
	protected Usage usage;

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
	
	@JsonProperty("usage")
	public Usage getUsage() {
		return usage;
	}

	public void setUsage(final Usage usage) {
		this.usage = usage;
	}
}
