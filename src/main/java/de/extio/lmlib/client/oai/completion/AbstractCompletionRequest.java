package de.extio.lmlib.client.oai.completion;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class AbstractCompletionRequest {
	
	protected String model;
	
	protected Integer maxTokens;
	
	protected Double temperature;
	
	protected Double topP;
	
	protected Boolean stream;
	
	protected StreamOptions streamOptions;
	
	protected Boolean usage;
	
	@JsonProperty("model")
	public String getModel() {
		return this.model;
	}
	
	public void setModel(final String model) {
		this.model = model;
	}
	
	@JsonProperty("max_tokens")
	public Integer getMaxTokens() {
		return this.maxTokens;
	}
	
	public void setMaxTokens(final Integer maxTokens) {
		this.maxTokens = maxTokens;
	}
	
	@JsonProperty("temperature")
	public Double getTemperature() {
		return this.temperature;
	}
	
	public void setTemperature(final Double temperature) {
		this.temperature = temperature;
	}
	
	@JsonProperty("top_p")
	public Double getTopP() {
		return this.topP;
	}
	
	public void setTopP(final Double topP) {
		this.topP = topP;
	}
	
	@JsonProperty("stream")
	public Boolean isStream() {
		return this.stream;
	}
	
	public void setStream(final Boolean stream) {
		this.stream = stream;
	}
	
	@JsonProperty("stream_options")
	public StreamOptions getStreamOptions() {
		return streamOptions;
	}
	
	public void setStreamOptions(final StreamOptions streamOptions) {
		this.streamOptions = streamOptions;
	}
	
	@JsonProperty("usage")
	public Boolean getUsage() {
		return this.usage;
	}
	
	public void setUsage(final Boolean usage) {
		this.usage = usage;
	}
}
