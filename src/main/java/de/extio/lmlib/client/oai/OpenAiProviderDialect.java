package de.extio.lmlib.client.oai;

import de.extio.lmlib.profile.ModelProfile;

public interface OpenAiProviderDialect {

	default boolean sendUsage(final ModelProfile modelProfile) {
		return true;
	}

	default boolean sendReasoning(final ModelProfile modelProfile) {
		return true;
	}

	default boolean sendMaxCompletionTokens(final ModelProfile modelProfile) {
		return true;
	}

	default String reasoningEffort(final ModelProfile modelProfile) {
		return "medium";
	}

	default String reasoningSummaryDetails(final ModelProfile modelProfile) {
		return "concise";
	}
}
