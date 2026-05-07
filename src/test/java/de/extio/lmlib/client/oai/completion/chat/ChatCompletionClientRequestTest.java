package de.extio.lmlib.client.oai.completion.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.extio.lmlib.client.oai.OpenAiProviderDialect;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;

class ChatCompletionClientRequestTest {

	@Test
	void defaultsToAllRequestFieldsWhenNoDialectIsConfigured() {
		final var client = new TestChatCompletionClient();
		final var request = client.createRequest(
				List.of(new ChatMessage("user", "hello", null)),
				this.createProfile(),
				false,
				null);

		assertTrue(request.getUsage());
		assertNotNull(request.getReasoning());
		assertEquals(ChatCompletionRequest.ChatCompletionsRequestReasoningEffort.medium, request.getReasoning().getEffort());
		assertEquals("concise", request.getReasoning().getSummary());
		assertEquals(2048, request.getMaxCompletionTokens());
	}

	@Test
	void omitsDialectDisabledRequestFields() {
		final var client = new TestChatCompletionClient();
		client.setDialect(new OpenAiProviderDialect() {
			@Override
			public boolean sendUsage(final ModelProfile modelProfile) {
				return false;
			}

			@Override
			public boolean sendReasoning(final ModelProfile modelProfile) {
				return false;
			}

			@Override
			public boolean sendMaxCompletionTokens(final ModelProfile modelProfile) {
				return false;
			}
		});

		final var request = client.createRequest(
				List.of(new ChatMessage("user", "hello", null)),
				this.createProfile(),
				false,
				null);

		assertNull(request.getUsage());
		assertNull(request.getReasoning());
		assertNull(request.getMaxCompletionTokens());
		assertEquals(2048, request.getMaxTokens());
		assertFalse(request.isStream());
	}

	@Test
	void usesDialectProvidedReasoningDefaults() {
		final var client = new TestChatCompletionClient();
		client.setDialect(new OpenAiProviderDialect() {
			@Override
			public String reasoningEffort(final ModelProfile modelProfile) {
				return "high";
			}

			@Override
			public String reasoningSummaryDetails(final ModelProfile modelProfile) {
				return "detailed";
			}
		});

		final var request = client.createRequest(
				List.of(new ChatMessage("user", "hello", null)),
				this.createProfile(),
				false,
				null);

		assertNotNull(request.getReasoning());
		assertEquals(ChatCompletionRequest.ChatCompletionsRequestReasoningEffort.high, request.getReasoning().getEffort());
		assertEquals("detailed", request.getReasoning().getSummary());
	}

	private ModelProfile createProfile() {
		return new ModelProfile(
				"",
				"fallback",
				"cl100k_base",
				2048,
				8192,
				0.4,
				1.0,
				ModelProvider.OAI_CHAT_COMPLETION,
				"test-model",
				"http://localhost:5001",
				"",
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				"TEST");
	}

	private static final class TestChatCompletionClient extends ChatCompletionClient {

		private void setDialect(final OpenAiProviderDialect openAiProviderDialect) {
			this.openAiProviderDialect = openAiProviderDialect;
		}
	}
}
