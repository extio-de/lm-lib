package de.extio.lmlib.profile;

import java.math.BigDecimal;

public record ModelProfile(
		String prompt,
		String tokenEncoding,
		int maxTokens,
		int maxContextLength,
		double temperature,
		double topP,
		ModelProvider modelProvider,
		String modelName,
		String url,
		String apiKey,
		BigDecimal costPerInToken,
		BigDecimal costPerCachedInToken,
		BigDecimal costPerOutToken,
		BigDecimal costPerReasoningOutToken,
		String reasoningEffort,
		String reasoningSummaryDetails,
		String category) {
	
	public ModelProfile {
		if (maxTokens <= 0) {
			throw new IllegalArgumentException("Model profile maxTokens must be greater than zero");
		}
		if (maxContextLength <= 0) {
			throw new IllegalArgumentException("Model profile maxContextLength must be greater than zero");
		}
		if (temperature <= 0) {
			throw new IllegalArgumentException("Model profile temperature must be greater than zero");
		}
		if (topP <= 0) {
			throw new IllegalArgumentException("Model profile topP must be greater than zero");
		}
		if (modelProvider == null) {
			throw new IllegalArgumentException("Model profile modelProvider is mandatory and cannot be null");
		}
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("Model profile url is mandatory and cannot be empty");
		}
		if (category == null || category.isBlank()) {
			throw new IllegalArgumentException("Model profile category is mandatory and cannot be empty");
		}
	}
	
	public static enum ModelProvider {
		OAI_TEXT_COMPLETION, OAI_CHAT_COMPLETION;
	}
}
