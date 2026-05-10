package de.extio.lmlib.client.oai.completion;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.*;

public final class Usage {
	
	private int completionTokens;
	
	private int promptTokens;
	
	private int totalTokens;
	
	private InputTokensDetails inputTokensDetails;
	
	private OutputTokensDetails outputTokensDetails;

	private BigDecimal cost;

	private Boolean byok;

	private CostDetails costDetails;
	
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

	@JsonProperty("prompt_tokens_details")
	public void setPromptTokensDetails(final InputTokensDetails inputTokensDetails) {
		this.inputTokensDetails = inputTokensDetails;
	}
	
	@JsonProperty("output_tokens_details")
	public OutputTokensDetails getOutputTokensDetails() {
		return this.outputTokensDetails;
	}
	
	public void setOutputTokensDetails(final OutputTokensDetails outputTokensDetails) {
		this.outputTokensDetails = outputTokensDetails;
	}

	@JsonProperty("completion_tokens_details")
	public void setCompletionTokensDetails(final OutputTokensDetails outputTokensDetails) {
		this.outputTokensDetails = outputTokensDetails;
	}

	@JsonProperty("cost")
	public BigDecimal getCost() {
		return this.cost;
	}

	public void setCost(final BigDecimal cost) {
		this.cost = cost;
	}

	@JsonProperty("is_byok")
	public Boolean getByok() {
		return this.byok;
	}

	public void setByok(final Boolean byok) {
		this.byok = byok;
	}

	@JsonProperty("cost_details")
	public CostDetails getCostDetails() {
		return this.costDetails;
	}

	public void setCostDetails(final CostDetails costDetails) {
		this.costDetails = costDetails;
	}
	
	public int getCachedTokens() {
		return this.inputTokensDetails != null ? this.inputTokensDetails.getCachedTokens() : 0;
	}
	
	public int getReasoningTokens() {
		return this.outputTokensDetails != null ? this.outputTokensDetails.getReasoningTokens() : 0;
	}
	
	@Override
	public String toString() {
		return "Usage [completionTokens=" + this.completionTokens + ", promptTokens=" + this.promptTokens + ", totalTokens=" + this.totalTokens + ", inputTokensDetails=" + this.inputTokensDetails + ", outputTokensDetails=" + this.outputTokensDetails + ", cost=" + this.cost + ", byok=" + this.byok + ", costDetails=" + this.costDetails + "]";
	}

	public static final class CostDetails {
		
		private BigDecimal upstreamInferenceCost;
		
		private BigDecimal upstreamInferencePromptCost;
		
		private BigDecimal upstreamInferenceCompletionsCost;
		
		private BigDecimal upstreamInferenceInputCost;
		
		private BigDecimal upstreamInferenceOutputCost;
		
		@JsonProperty("upstream_inference_cost")
		public BigDecimal getUpstreamInferenceCost() {
			return this.upstreamInferenceCost;
		}

		public void setUpstreamInferenceCost(final BigDecimal upstreamInferenceCost) {
			this.upstreamInferenceCost = upstreamInferenceCost;
		}

		@JsonProperty("upstream_inference_prompt_cost")
		public BigDecimal getUpstreamInferencePromptCost() {
			return this.upstreamInferencePromptCost;
		}

		public void setUpstreamInferencePromptCost(final BigDecimal upstreamInferencePromptCost) {
			this.upstreamInferencePromptCost = upstreamInferencePromptCost;
		}

		@JsonProperty("upstream_inference_completions_cost")
		public BigDecimal getUpstreamInferenceCompletionsCost() {
			return this.upstreamInferenceCompletionsCost;
		}

		public void setUpstreamInferenceCompletionsCost(final BigDecimal upstreamInferenceCompletionsCost) {
			this.upstreamInferenceCompletionsCost = upstreamInferenceCompletionsCost;
		}

		@JsonProperty("upstream_inference_input_cost")
		public BigDecimal getUpstreamInferenceInputCost() {
			return this.upstreamInferenceInputCost;
		}

		public void setUpstreamInferenceInputCost(final BigDecimal upstreamInferenceInputCost) {
			this.upstreamInferenceInputCost = upstreamInferenceInputCost;
		}

		@JsonProperty("upstream_inference_output_cost")
		public BigDecimal getUpstreamInferenceOutputCost() {
			return this.upstreamInferenceOutputCost;
		}

		public void setUpstreamInferenceOutputCost(final BigDecimal upstreamInferenceOutputCost) {
			this.upstreamInferenceOutputCost = upstreamInferenceOutputCost;
		}

		@Override
		public String toString() {
			return "CostDetails [upstreamInferenceCost=" + this.upstreamInferenceCost + ", upstreamInferencePromptCost=" + this.upstreamInferencePromptCost + ", upstreamInferenceCompletionsCost=" + this.upstreamInferenceCompletionsCost + ", upstreamInferenceInputCost=" + this.upstreamInferenceInputCost + ", upstreamInferenceOutputCost=" + this.upstreamInferenceOutputCost + "]";
		}
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
