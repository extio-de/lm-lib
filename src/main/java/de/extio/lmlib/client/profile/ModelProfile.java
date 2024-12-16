package de.extio.lmlib.client.profile;

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
		String apiKey) {
	
	public static enum ModelProvider {
		AZURE_AI, OAI_TEXT_COMPLETION;
	}
}
