package de.extio.lmlib.client.oai.completion;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class Usage {
	
	private int completionTokens;
	
	private int promptTokens;
	
	private int totalTokens;
	
	private InputTokensDetails inputTokensDetails;
	
	private OutputTokensDetails outputTokensDetails;
	
	@JsonProperty("completion_tokens")
	public int getCompletionTokens() {
		return this.completionTokens;
	}
	
	public void setCompletionTokens(final int completionTokens) {
		this.completionTokens = completionTokens;
	}
	
	@JsonProperty("output_tokens")
	public void setOutputTokens(final int outputTokens) {
		this.completionTokens = outputTokens;
	}
	
	@JsonProperty("prompt_tokens")
	public int getPromptTokens() {
		return this.promptTokens;
	}
	
	public void setPromptTokens(final int promptTokens) {
		this.promptTokens = promptTokens;
	}
	
	@JsonProperty("input_tokens")
	public void setInputTokens(final int inputTokens) {
		this.promptTokens = inputTokens;
	}
	
	@JsonProperty("total_tokens")
	public int getTotalTokens() {
		return this.totalTokens;
	}
	
	public void setTotalTokens(final int totalTokens) {
		this.totalTokens = totalTokens;
	}
	
	@JsonProperty("input_tokens_details")
	public InputTokensDetails getInputTokensDetails() {
		return this.inputTokensDetails;
	}
	
	public void setInputTokensDetails(final InputTokensDetails inputTokensDetails) {
		this.inputTokensDetails = inputTokensDetails;
	}
	
	@JsonProperty("output_tokens_details")
	public OutputTokensDetails getOutputTokensDetails() {
		return this.outputTokensDetails;
	}
	
	public void setOutputTokensDetails(final OutputTokensDetails outputTokensDetails) {
		this.outputTokensDetails = outputTokensDetails;
	}
	
	public int getCachedTokens() {
		return this.inputTokensDetails != null ? this.inputTokensDetails.getCachedTokens() : 0;
	}
	
	public int getReasoningTokens() {
		return this.outputTokensDetails != null ? this.outputTokensDetails.getReasoningTokens() : 0;
	}
	
	@Override
	public String toString() {
		return "Usage [completionTokens=" + this.completionTokens + ", promptTokens=" + this.promptTokens + ", totalTokens=" + this.totalTokens + ", inputTokensDetails=" + this.inputTokensDetails + ", outputTokensDetails=" + this.outputTokensDetails + "]";
	}
	
	public static final class InputTokensDetails {
		
		private int cachedTokens;
		
		@JsonProperty("cached_tokens")
		public int getCachedTokens() {
			return this.cachedTokens;
		}
		
		public void setCachedTokens(final int cachedTokens) {
			this.cachedTokens = cachedTokens;
		}
		
		@Override
		public String toString() {
			return "InputTokensDetails [cachedTokens=" + this.cachedTokens + "]";
		}
	}
	
	public static final class OutputTokensDetails {
		
		private int reasoningTokens;
		
		@JsonProperty("reasoning_tokens")
		public int getReasoningTokens() {
			return this.reasoningTokens;
		}
		
		public void setReasoningTokens(final int reasoningTokens) {
			this.reasoningTokens = reasoningTokens;
		}
		
		@Override
		public String toString() {
			return "OutputTokensDetails [reasoningTokens=" + this.reasoningTokens + "]";
		}
	}
	
}
