package de.extio.lmlib.client.anthropic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.Conversation.TurnType;
import de.extio.lmlib.client.ToolCall;
import de.extio.lmlib.client.ToolCallData;
import de.extio.lmlib.client.ToolDefinition;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;
import de.extio.lmlib.token.TokenizerResolver;
import tools.jackson.databind.json.JsonMapper;

class AnthropicClientRequestTest {

	@Test
	void sendsSystemAsTopLevelParameterAndPlainMessages() {
		final var client = new TestAnthropicClient();
		final var conversation = Conversation.create("answer briefly", "hello");
		conversation.addTurn(new Turn(TurnType.ASSISTANT, "hi there"));
		conversation.addTurn(new Turn(TurnType.USER, "again"));

		final var request = client.createRequest(conversation, this.createProfile(2048), false, null);

		assertEquals("answer briefly", request.system());
		assertEquals("test-model", request.model());
		assertEquals(Integer.valueOf(2048), request.maxTokens());
		assertEquals(Boolean.FALSE, request.stream());
		assertEquals(3, request.messages().size());
		assertEquals("user", request.messages().get(0).role());
		assertEquals("hello", request.messages().get(0).content());
		assertEquals("assistant", request.messages().get(1).role());
		assertEquals("hi there", request.messages().get(1).content());
		assertEquals("again", request.messages().get(2).content());
		assertNull(request.tools());
		assertNull(request.toolChoice());
		assertNull(request.thinking());
	}

	@Test
	void coalescesToolResultsIntoSingleUserMessage() {
		final var client = new TestAnthropicClient();
		final var firstCall = new ToolCall("toolu_1", "tool_use", "get_weather", "{\"city\":\"Berlin\"}");
		final var secondCall = new ToolCall("toolu_2", "tool_use", "get_time", "{\"zone\":\"CET\"}");
		final var conversation = Conversation.create("first?");
		conversation.addTurn(new Turn(TurnType.ASSISTANT, "", List.of(firstCall, secondCall), null));
		conversation.addTurn(new Turn(TurnType.TOOL, "sunny", null, "toolu_1"));
		conversation.addTurn(new Turn(TurnType.TOOL, "noon", null, "toolu_2"));

		final var messages = client.createMessages(conversation, new StringBuilder());

		assertEquals(3, messages.size());
		assertEquals("user", messages.get(0).role());

		final var assistantBlocks = this.blocks(messages.get(1));
		assertEquals(2, assistantBlocks.size());
		final var toolUse = assertInstanceOf(AnthropicContentBlock.class, assistantBlocks.get(0));
		assertEquals("tool_use", toolUse.type());
		assertEquals("toolu_1", toolUse.id());
		assertEquals(Map.of("city", "Berlin"), toolUse.input());
		final var secondToolUse = assertInstanceOf(AnthropicContentBlock.class, assistantBlocks.get(1));
		assertEquals("toolu_2", secondToolUse.id());

		final var toolResultBlocks = this.blocks(messages.get(2));
		assertEquals(2, toolResultBlocks.size());
		final var firstResult = assertInstanceOf(AnthropicContentBlock.class, toolResultBlocks.get(0));
		assertEquals("tool_result", firstResult.type());
		assertEquals("toolu_1", firstResult.toolUseId());
		assertEquals("sunny", firstResult.content());
		assertEquals("toolu_2", assertInstanceOf(AnthropicContentBlock.class, toolResultBlocks.get(1)).toolUseId());
	}

	@Test
	void replaysThinkingBlocksBeforeToolUseBlocks() {
		final var client = new TestAnthropicClient();
		final var toolCall = new ToolCall("toolu_1", "tool_use", "get_weather", "{}");
		final Map<String, Object> thinkingItem = Map.of("type", "thinking", "thinking", "hmm", "signature", "sig");
		final var conversation = Conversation.create("q");
		conversation.addTurn(new Turn(TurnType.ASSISTANT, "", List.of(toolCall), null, null, List.of(thinkingItem)));

		final var messages = client.createMessages(conversation, new StringBuilder());

		final var blocks = this.blocks(messages.get(1));
		assertEquals(2, blocks.size());
		assertEquals(thinkingItem, blocks.get(0));
		final var toolUse = assertInstanceOf(AnthropicContentBlock.class, blocks.get(1));
		assertEquals("tool_use", toolUse.type());
	}

	@Test
	void mapsToolChoiceVariants() {
		final var client = new TestAnthropicClient();
		final var tools = List.of(new ToolDefinition("get_weather", "weather", Map.of("type", "object"), null));

		final var required = client.createToolChoice(new ToolCallData(tools, ToolCallData.TOOL_CHOICE_REQUIRED, null, null));
		assertEquals("any", required.type());

		final var forced = client.createToolChoice(new ToolCallData(tools, ToolCallData.TOOL_CHOICE_AUTO, "get_weather", null));
		assertEquals("tool", forced.type());
		assertEquals("get_weather", forced.name());

		final var none = client.createToolChoice(new ToolCallData(tools, ToolCallData.TOOL_CHOICE_NONE, null, null));
		assertEquals("none", none.type());

		final var auto = client.createToolChoice(new ToolCallData(tools, ToolCallData.TOOL_CHOICE_AUTO, null, null));
		assertEquals("auto", auto.type());
		assertNull(auto.disableParallelToolUse());

		final var sequential = client.createToolChoice(new ToolCallData(tools, null, null, Boolean.FALSE));
		assertEquals("auto", sequential.type());
		assertEquals(Boolean.TRUE, sequential.disableParallelToolUse());

		final var unset = client.createToolChoice(new ToolCallData(tools, null, null, null));
		assertNull(unset);
	}

	@Test
	void clampsThinkingBudgetToTokenLimit() {
		final var client = new TestAnthropicClient();

		client.anthropicDialect = new AnthropicDialect() {
			@Override
			public Integer thinkingBudgetTokens(final ModelProfile modelProfile) {
				return Integer.valueOf(4096);
			}
		};
		final var clamped = client.createThinkingConfig(this.createProfile(2500));
		assertEquals("enabled", clamped.type());
		assertEquals(Integer.valueOf(2499), clamped.budgetTokens());

		client.anthropicDialect = new AnthropicDialect() {
			@Override
			public Integer thinkingBudgetTokens(final ModelProfile modelProfile) {
				return Integer.valueOf(1500);
			}
		};
		final var fitted = client.createThinkingConfig(this.createProfile(2500));
		assertEquals(Integer.valueOf(1500), fitted.budgetTokens());

		client.anthropicDialect = new AnthropicDialect() {
			@Override
			public Integer thinkingBudgetTokens(final ModelProfile modelProfile) {
				return Integer.valueOf(512);
			}
		};
		assertNull(client.createThinkingConfig(this.createProfile(2500)));

		client.anthropicDialect = null;
		assertNull(client.createThinkingConfig(this.createProfile(2500)));
	}

	@Test
	void mapsNonStreamResponseToCompletion() {
		final var client = new TestAnthropicClient();
		client.tokenizerResolver = new CountingTokenizerResolver();
		final var json = """
				{
				  "id": "msg_1",
				  "type": "message",
				  "role": "assistant",
				  "model": "test-model",
				  "content": [
				    {"type": "thinking", "thinking": "reasoning text", "signature": "sig1"},
				    {"type": "text", "text": "Hello "},
				    {"type": "text", "text": "world"},
				    {"type": "tool_use", "id": "toolu_1", "name": "get_weather", "input": {"city": "Berlin"}}
				  ],
				  "stop_reason": "tool_use",
				  "stop_sequence": null,
				  "usage": {"input_tokens": 100, "output_tokens": 42, "cache_creation_input_tokens": 5, "cache_read_input_tokens": 7}
				}
				""";
		final var response = JsonMapper.builder().build().readValue(json, AnthropicMessageResponse.class);

		final var completion = client.mapResponse(response, this.createProfile(2048), LocalDateTime.now(), Conversation.create("hi"));

		assertEquals("Hello world", completion.response());
		assertEquals("reasoning text", completion.reasoning());
		assertEquals(CompletionFinishReason.TOOL_CALLS, completion.finishReason());
		assertEquals(1, completion.toolCalls().size());
		final var toolCall = completion.toolCalls().get(0);
		assertEquals("toolu_1", toolCall.id());
		assertEquals("get_weather", toolCall.name());
		assertEquals("{\"city\":\"Berlin\"}", toolCall.arguments());
		assertEquals(1, completion.outputItems().size());
		assertEquals("thinking", completion.outputItems().get(0).get("type"));
		assertEquals("reasoning text", completion.outputItems().get(0).get("thinking"));
		assertEquals("sig1", completion.outputItems().get(0).get("signature"));
		// input_tokens + cache_creation_input_tokens
		assertEquals(105, completion.statistics().inTokens());
		assertEquals(7, completion.statistics().cachedInTokens());
		// output_tokens minus tokenizer-estimated reasoning (fixed count of 3)
		assertEquals(39, completion.statistics().outTokens());
		assertEquals(3, completion.statistics().reasoningOutTokens());
	}

	@Test
	void mapsFinishReasons() {
		final var client = new TestAnthropicClient();
		assertEquals(CompletionFinishReason.DONE, client.mapFinishReason("end_turn", List.of()));
		assertEquals(CompletionFinishReason.DONE, client.mapFinishReason("stop_sequence", List.of()));
		assertEquals(CompletionFinishReason.DONE, client.mapFinishReason(null, List.of()));
		assertEquals(CompletionFinishReason.TOKEN_LIMIT_REACHED, client.mapFinishReason("max_tokens", List.of()));
		assertEquals(CompletionFinishReason.CONTENT_FILTERED, client.mapFinishReason("refusal", List.of()));
		assertEquals(CompletionFinishReason.TOOL_CALLS, client.mapFinishReason("tool_use", List.of(new ToolCall("id", "tool_use", "n", "{}"))));
		assertEquals(CompletionFinishReason.TOOL_CALLS, client.mapFinishReason("end_turn", List.of(new ToolCall("id", "tool_use", "n", "{}"))));
	}

	@Test
	void readsStreamedResponseAndEmitsChunks() throws Exception {
		final var client = new TestAnthropicClient();
		client.tokenizerResolver = new CountingTokenizerResolver();
		final var sse = """
				event: message_start
				data: {"type": "message_start", "message": {"id": "msg_1", "type": "message", "role": "assistant", "model": "test-model", "content": [], "usage": {"input_tokens": 57, "output_tokens": 0}}}

				: keep-alive

				event: content_block_start
				data: {"type": "content_block_start", "index": 0, "content_block": {"type": "thinking", "thinking": "", "signature": ""}}

				event: content_block_delta
				data: {"type": "content_block_delta", "index": 0, "delta": {"type": "thinking_delta", "thinking": "hmm "}}

				event: content_block_delta
				data: {"type": "content_block_delta", "index": 0, "delta": {"type": "thinking_delta", "thinking": "yes"}}

				event: content_block_stop
				data: {"type": "content_block_stop", "index": 0}

				event: content_block_start
				data: {"type": "content_block_start", "index": 1, "content_block": {"type": "text", "text": ""}}

				event: content_block_delta
				data: {"type": "content_block_delta", "index": 1, "delta": {"type": "text_delta", "text": "hello stream"}}

				event: content_block_stop
				data: {"type": "content_block_stop", "index": 1}

				event: message_delta
				data: {"type": "message_delta", "delta": {"stop_reason": "end_turn", "stop_sequence": null}, "usage": {"input_tokens": 57, "cache_read_input_tokens": 0, "output_tokens": 30}}

				event: message_stop
				data: {"type": "message_stop"}

				""";
		final var chunks = new ArrayList<Chunk>();

		final var response = client.readStream(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)), chunks::add);

		final var reasoningChunks = chunks.stream().filter(chunk -> chunk.reasoningContent() != null).toList();
		final var textChunks = chunks.stream().filter(chunk -> chunk.content() != null).toList();
		assertEquals(2, reasoningChunks.size());
		assertEquals(1, textChunks.size());
		assertEquals("hello stream", textChunks.get(0).content());

		assertEquals("msg_1", response.id());
		assertEquals("end_turn", response.stopReason());
		assertEquals(Integer.valueOf(30), response.usage().outputTokens());
		assertEquals(2, response.content().size());
		assertEquals("hmm yes", response.content().get(0).thinking());
		assertEquals("hello stream", response.content().get(1).text());

		final var completion = client.mapResponse(response, this.createProfile(2048), LocalDateTime.now(), Conversation.create("hi"));
		assertEquals("hello stream", completion.response());
		assertEquals("hmm yes", completion.reasoning());
		assertEquals(CompletionFinishReason.DONE, completion.finishReason());
	}

	@Test
	void failsOnStreamErrorEvent() {
		final var client = new TestAnthropicClient();
		final var sse = """
				event: error
				data: {"type": "error", "error": {"type": "overloaded_error", "message": "Overloaded"}}

				""";
		final var exception = assertThrows(IllegalStateException.class,
				() -> client.readStream(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)), chunk -> {
				}));
		assertTrue(exception.getMessage().contains("Overloaded"));
	}

	@Test
	void requiresPositiveMaxTokens() {
		final var client = new TestAnthropicClient();
		assertThrows(IllegalStateException.class, () -> client.createRequest(Conversation.create("hi"), this.createProfile(0), false, null));
	}

	@Test
	void resolvesMessagesPathVariants() {
		assertEquals("/v1/messages", AnthropicClient.messagesPath("http://127.0.0.1:5001"));
		assertEquals("/messages", AnthropicClient.messagesPath("http://127.0.0.1:5001/v1"));
		assertEquals("/messages", AnthropicClient.messagesPath("http://127.0.0.1:5001/v1/"));
		assertEquals("http://127.0.0.1:5001", AnthropicClient.stripMessagesSuffix("http://127.0.0.1:5001/v1/messages"));
		assertEquals("http://127.0.0.1:5001", AnthropicClient.stripMessagesSuffix("http://127.0.0.1:5001"));
	}

	@Test
	void resolvesModelsPathVariants() {
		assertEquals("/v1/models", AnthropicClient.modelsPath("http://127.0.0.1:5001"));
		assertEquals("/models", AnthropicClient.modelsPath("http://127.0.0.1:5001/v1"));
		assertEquals("/models", AnthropicClient.modelsPath("http://127.0.0.1:5001/v1/"));
	}

	@Test
	void parsesModelListingResponse() {
		final var mapper = JsonMapper.builder().build();
		final var official = mapper.readValue("""
				{"data": [{"type": "model", "id": "claude-sonnet-4-5", "display_name": "Claude Sonnet 4.5", "created_at": "2025-09-29T00:00:00Z"}], "has_more": false}
				""", AnthropicModelListResponse.class);
		assertEquals("claude-sonnet-4-5", official.data().get(0).id());
		assertEquals("Claude Sonnet 4.5", official.data().get(0).displayName());

		final var openAiShaped = mapper.readValue("""
				{"object": "list", "data": [{"id": "local-model", "object": "model", "status": {"value": "loaded"}}]}
				""", AnthropicModelListResponse.class);
		assertEquals("local-model", openAiShaped.data().get(0).id());
	}

	@Test
	void parsesModelCapabilities() {
		final var mapper = JsonMapper.builder().build();
		final var response = mapper.readValue("""
				{"data": [{
				  "type": "model",
				  "id": "claude-opus-5-5",
				  "capabilities": {
				    "image_input": {"supported": true},
				    "thinking": {"supported": true, "types": {"adaptive": {"supported": true}, "disabled": {"supported": true}, "enabled": {"supported": false}}}
				  }
				}, {
				  "type": "model",
				  "id": "claude-haiku-no-thinking",
				  "capabilities": {"thinking": {"supported": false}}
				}, {
				  "type": "model",
				  "id": "tool-less-model",
				  "capabilities": {"tool_use": {"supported": false}}
				}, {
				  "type": "model",
				  "id": "no-capabilities-model"
				}]}
				""", AnthropicModelListResponse.class);
		final var models = response.data().stream().collect(java.util.stream.Collectors.toMap(AnthropicModel::id, model -> model));

		final var opus = models.get("claude-opus-5-5");
		assertTrue(opus.capabilities().imageInput().isSupported());
		assertTrue(AnthropicClient.supportsToolCalling(opus));
		// thinking.types.enabled explicitly false -> extended thinking unsupported
		assertFalse(AnthropicClient.supportsExtendedThinking(opus));

		assertFalse(AnthropicClient.supportsExtendedThinking(models.get("claude-haiku-no-thinking")));
		assertFalse(AnthropicClient.supportsToolCalling(models.get("tool-less-model")));

		// missing capabilities skip the checks
		assertTrue(AnthropicClient.supportsToolCalling(models.get("no-capabilities-model")));
		assertTrue(AnthropicClient.supportsExtendedThinking(models.get("no-capabilities-model")));
		assertTrue(AnthropicClient.supportsToolCalling((AnthropicModel) null));
		assertTrue(AnthropicClient.supportsExtendedThinking(null));
	}

	@Test
	void modelDiscoveryNeverFails() {
		final var client = new TestAnthropicClient();
		// no rest client configured - discovery must degrade to an empty listing
		assertEquals(List.of(), client.getModelNames(this.createProfile(2048), true));
		assertTrue(client.supportsToolCalling(this.createProfile(2048)));
	}

	@SuppressWarnings("unchecked")
	private List<Object> blocks(final AnthropicMessage message) {
		return (List<Object>) message.content();
	}

	private ModelProfile createProfile(final int maxTokens) {
		return new ModelProfile(
				"",
				"fallback",
				"cl100k_base",
				maxTokens,
				8192,
				0.4,
				1.0,
				ModelProvider.ANTHROPIC,
				"test-model",
				"http://localhost:5001",
				"test-key",
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				"TEST");
	}

	private static final class CountingTokenizerResolver extends TokenizerResolver {

		private CountingTokenizerResolver() {
			super(List.of());
		}

		@Override
		public int count(final String txt, final ModelProfile modelProfile) {
			return 3;
		}
	}

	private static final class TestAnthropicClient extends AnthropicClient {
	}
}
