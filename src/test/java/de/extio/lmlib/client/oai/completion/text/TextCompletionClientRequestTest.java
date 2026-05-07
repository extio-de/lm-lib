package de.extio.lmlib.client.oai.completion.text;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import de.extio.lmlib.client.oai.OpenAiProviderDialect;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;

class TextCompletionClientRequestTest {

	@Test
	void omitsUsageWhenNoDialectIsConfigured() {
		final var client = new TestTextCompletionClient();
		final var request = client.createRequest("prompt", this.createProfile(), false);

		assertNull(request.getUsage());
	}

	@Test
	void includesUsageWhenDialectEnablesIt() {
		final var client = new TestTextCompletionClient();
		client.setDialect(new OpenAiProviderDialect() {
			@Override
			public boolean sendUsage(final ModelProfile modelProfile) {
				return true;
			}
		});

		final var request = client.createRequest("prompt", this.createProfile(), false);

		assertTrue(request.getUsage());
	}

	private ModelProfile createProfile() {
		return new ModelProfile(
				"gpt-oss",
				"fallback",
				"cl100k_base",
				2048,
				8192,
				0.4,
				1.0,
				ModelProvider.OAI_TEXT_COMPLETION,
				"test-model",
				"http://localhost:5001",
				"",
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				"TEST");
	}

	private static final class TestTextCompletionClient extends TextCompletionClient {

		private void setDialect(final OpenAiProviderDialect openAiProviderDialect) {
			this.openAiProviderDialect = openAiProviderDialect;
		}
	}
}
