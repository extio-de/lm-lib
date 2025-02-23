package de.extio.lmlib.profile;

import java.math.BigDecimal;

public record ModelProfile(
		String prompt,
		String tokenEncoding,
		int maxTokens,
		int maxContextLength,
		double temperature,
		double topP,
		int maxContinuations,
		ModelProvider modelProvider,
		String modelName,
		String url,
		String apiKey,
		BigDecimal costInToken,
		BigDecimal costOutToken) {
	
	public static enum ModelProvider {
		AZURE_AI, OAI_TEXT_COMPLETION;
	}
}
