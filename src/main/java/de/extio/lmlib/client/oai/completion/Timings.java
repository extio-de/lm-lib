package de.extio.lmlib.client.oai.completion;

import com.fasterxml.jackson.annotation.*;

public final class Timings {
	
	private int cacheN;
	
	private int predictedN;
	
	private double predictedMs;
	
	private double predictedPerTokenMs;
	
	private double predictedPerSecond;
	
	private int promptN;
	
	private double promptMs;
	
	private double promptPerTokenMs;
	
	private double promptPerSecond;
	
	@JsonProperty("cache_n")
	public int getCacheN() {
		return this.cacheN;
	}
	
	public void setCacheN(final int cacheN) {
		this.cacheN = cacheN;
	}
	
	@JsonProperty("predicted_n")
	public int getPredictedN() {
		return this.predictedN;
	}
	
	public void setPredictedN(final int predictedN) {
		this.predictedN = predictedN;
	}
	
	@JsonProperty("predicted_ms")
	public double getPredictedMs() {
		return this.predictedMs;
	}
	
	public void setPredictedMs(final double predictedMs) {
		this.predictedMs = predictedMs;
	}
	
	@JsonProperty("predicted_per_token_ms")
	public double getPredictedPerTokenMs() {
		return this.predictedPerTokenMs;
	}
	
	public void setPredictedPerTokenMs(final double predictedPerTokenMs) {
		this.predictedPerTokenMs = predictedPerTokenMs;
	}
	
	@JsonProperty("predicted_per_second")
	public double getPredictedPerSecond() {
		return this.predictedPerSecond;
	}
	
	public void setPredictedPerSecond(final double predictedPerSecond) {
		this.predictedPerSecond = predictedPerSecond;
	}
	
	@JsonProperty("prompt_n")
	public int getPromptN() {
		return this.promptN;
	}
	
	public void setPromptN(final int promptN) {
		this.promptN = promptN;
	}
	
	@JsonProperty("prompt_ms")
	public double getPromptMs() {
		return this.promptMs;
	}
	
	public void setPromptMs(final double promptMs) {
		this.promptMs = promptMs;
	}
	
	@JsonProperty("prompt_per_token_ms")
	public double getPromptPerTokenMs() {
		return this.promptPerTokenMs;
	}
	
	public void setPromptPerTokenMs(final double promptPerTokenMs) {
		this.promptPerTokenMs = promptPerTokenMs;
	}
	
	@JsonProperty("prompt_per_second")
	public double getPromptPerSecond() {
		return this.promptPerSecond;
	}
	
	public void setPromptPerSecond(final double promptPerSecond) {
		this.promptPerSecond = promptPerSecond;
	}
	
	@Override
	public String toString() {
		return "Timings [cacheN=" + this.cacheN + ", predictedN=" + this.predictedN + ", predictedMs=" + this.predictedMs + ", predictedPerTokenMs=" + this.predictedPerTokenMs + ", predictedPerSecond=" + this.predictedPerSecond + ", promptN=" + this.promptN + ", promptMs=" + this.promptMs + ", promptPerTokenMs=" + this.promptPerTokenMs + ", promptPerSecond=" + this.promptPerSecond + "]";
	}
	
}
