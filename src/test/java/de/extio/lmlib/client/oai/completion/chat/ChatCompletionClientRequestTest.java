package de.extio.lmlib.client.oai.completion.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
	void defaultsToOfficialRequestFieldsWhenNoDialectIsConfigured() {
		final var client = new TestChatCompletionClient();
		final var request = client.createRequest(
				List.of(new ChatMessage("user", "hello", null)),
				this.createProfile(),
				false,
				null);

		assertNull(request.getUsage());
		assertEquals(OpenAiProviderDialect.ReasoningEffort.MEDIUM, request.getReasoningEffort());
		assertEquals(2048, request.getMaxCompletionTokens());
		assertNull(request.getMaxTokens());
	}

	@Test
	void omitsDialectDisabledReasoningAndUsesLegacyMaxTokens() {
		final var client = new TestChatCompletionClient();
		client.setDialect(new OpenAiProviderDialect() {
			@Override
			public boolean sendReasoning(final ModelProfile modelProfile) {
				return false;
			}

			@Override
			public ChatTokenLimitParameterMode chatTokenLimitParameterMode(final ModelProfile modelProfile) {
				return ChatTokenLimitParameterMode.MAX_TOKENS;
			}
		});

		final var request = client.createRequest(
				List.of(new ChatMessage("user", "hello", null)),
				this.createProfile(),
				false,
				null);

		assertNull(request.getUsage());
		assertNull(request.getReasoningEffort());
		assertNull(request.getMaxCompletionTokens());
		assertEquals(2048, request.getMaxTokens());
		assertFalse(request.isStream());
	}

	@Test
	void usesDialectProvidedReasoningEffort() {
		final var client = new TestChatCompletionClient();
		client.setDialect(new OpenAiProviderDialect() {
			@Override
			public ReasoningEffort reasoningEffort(final ModelProfile modelProfile) {
				return ReasoningEffort.HIGH;
			}
		});

		final var request = client.createRequest(
				List.of(new ChatMessage("user", "hello", null)),
				this.createProfile(),
				false,
				null);

		assertEquals(OpenAiProviderDialect.ReasoningEffort.HIGH, request.getReasoningEffort());
	}

	@Test
	void canEnableUsageAndSendBothTokenLimitFields() {
		final var client = new TestChatCompletionClient();
		client.setDialect(new OpenAiProviderDialect() {
			@Override
			public boolean sendUsage(final ModelProfile modelProfile) {
				return true;
			}

			@Override
			public ChatTokenLimitParameterMode chatTokenLimitParameterMode(final ModelProfile modelProfile) {
				return ChatTokenLimitParameterMode.BOTH;
			}
		});

		final var request = client.createRequest(
				List.of(new ChatMessage("user", "hello", null)),
				this.createProfile(),
				false,
				null);

		assertTrue(request.getUsage());
		assertEquals(2048, request.getMaxCompletionTokens());
		assertEquals(2048, request.getMaxTokens());
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
