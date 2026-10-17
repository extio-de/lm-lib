package de.extio.lmlib.client.anthropic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.CompletionStatistics;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.ProxyAuthorizationSupport;
import de.extio.lmlib.client.ToolCall;
import de.extio.lmlib.client.ToolCallData;
import de.extio.lmlib.client.ToolDefinition;
import de.extio.lmlib.client.anthropic.AnthropicStreamEvent.Delta;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;
import de.extio.lmlib.profile.ModelProfileService;
import de.extio.lmlib.token.TokenizerResolver;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link Client} implementation for the Anthropic Claude Messages API.
 * <p>
 * Implements the {@code POST /v1/messages} endpoint, including tool use with
 * {@code tool_use}/{@code tool_result} content blocks, server-sent event
 * streaming, and extended thinking (dialect-controlled). System turns are sent
 * as the top-level {@code system} parameter since the Messages API has no
 * system message role.
 */
public class AnthropicClient implements Client {

	private static final Logger LOGGER = LoggerFactory.getLogger(AnthropicClient.class);

	private static final String MESSAGES_PATH = "/v1/messages";

	private final Map<String, Map<String, AnthropicModel>> resolvedModels = new ConcurrentHashMap<>();

	private static final AnthropicDialect DEFAULT_ANTHROPIC_DIALECT = new AnthropicDialect() {
	};

	private static final TypeReference<Map<String, Object>> TOOL_INPUT_TYPE = new TypeReference<Map<String, Object>>() {
	};

	private final ObjectMapper objectMapper = JsonMapper.builder()
			.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
			.changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.build();

	@Autowired
	@Qualifier("lmLibRestClientBuilder")
	protected RestClient.Builder restClientBuilder;

	@Autowired
	protected TokenizerResolver tokenizerResolver;

	@Autowired
	protected ModelProfileService modelProfileService;

	@Autowired(required = false)
	protected AnthropicDialect anthropicDialect;

	@Autowired
	protected ProxyAuthorizationSupport proxyAuthorizationSupport;

	@Override
	public Completion conversation(final ModelCategory modelCategory, final Conversation conversation, final ToolCallData toolCallData, final boolean skipCache) {
		return this.streamConversation(modelCategory, conversation, null, toolCallData, skipCache);
	}

	@Override
	public Completion conversation(final ModelProfile modelProfile, final Conversation conversation, final ToolCallData toolCallData, final boolean skipCache) {
		return this.streamConversation(modelProfile, conversation, null, toolCallData, skipCache);
	}

	@Override
	public Completion streamConversation(final ModelCategory modelCategory_, final Conversation conversation, final Consumer<Chunk> chunkConsumer, final ToolCallData toolCallData, final boolean skipCache) {
		final var modelCategory = modelCategory_ == null ? ModelCategory.MEDIUM : modelCategory_;
		final var modelProfile = this.modelProfileService.getModelProfile(modelCategory.getModelProfile(), modelCategory);
		return this.streamConversation(modelProfile, conversation, chunkConsumer, toolCallData, skipCache);
	}

	@Override
	public Completion streamConversation(final ModelProfile modelProfile, final Conversation conversation, final Consumer<Chunk> chunkConsumer, final ToolCallData toolCallData, final boolean skipCache) {
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.ANTHROPIC) {
			throw new IllegalArgumentException("Invalid ModelProfile");
		}

		final var request = this.createRequest(conversation, modelProfile, chunkConsumer != null, toolCallData);
		final String requestBody;
		try {
			requestBody = this.objectMapper.writeValueAsString(request);
		}
		catch (final JacksonException e) {
			throw new IllegalStateException("Cannot serialize request body", e);
		}

		LOGGER.debug("Requesting Anthropic message at {}", modelProfile.url());
		final var start = LocalDateTime.now();
		final var base = stripMessagesSuffix(modelProfile.url());
		final var path = messagesPath(modelProfile.url());

		var requestSpec = this.restClientBuilder.baseUrl(base).build()
				.method(HttpMethod.POST)
				.uri(path)
				.header("Content-Type", "application/json")
				.header("anthropic-version", this.getAnthropicDialect().anthropicVersion(modelProfile));
		if (modelProfile.apiKey() != null && !modelProfile.apiKey().isBlank()) {
			requestSpec = requestSpec.header("x-api-key", modelProfile.apiKey());
		}
		requestSpec = this.proxyAuthorizationSupport.apply(requestSpec);

		final var response = requestSpec
				.body(requestBody)
				.exchange((clientRequest, clientResponse) -> {
					if (clientResponse.getStatusCode().isError()) {
						final var errorBody = new String(clientResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
						final var errorMessage = this.getErrorMessage(errorBody);
						LOGGER.warn("Error response from server: {} {}", clientResponse.getStatusCode(), errorMessage);
						throw new IllegalStateException("Error response from server: " + clientResponse.getStatusCode() + " " + errorMessage);
					}
					if (chunkConsumer == null) {
						return this.objectMapper.readValue(clientResponse.getBody(), AnthropicMessageResponse.class);
					}
					return this.readStream(clientResponse.getBody(), chunkConsumer);
				});

		return this.mapResponse(response, modelProfile, start, conversation);
	}

	@Override
	public List<String> getModelNames(final ModelProfile modelProfile, final boolean forceReload) {
		return this.resolveModels(modelProfile, forceReload).values().stream()
				.map(AnthropicModel::id)
				.filter(id -> id != null && !id.isBlank())
				.toList();
	}

	/**
	 * Loads and caches the model listing (including capabilities) per profile.
	 * Never fails: an unreachable or non-listing endpoint yields an empty map so
	 * capability checks are skipped instead of rejecting requests.
	 */
	private Map<String, AnthropicModel> resolveModels(final ModelProfile modelProfile, final boolean forceReload) {
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.ANTHROPIC) {
			return Map.of();
		}
		if (modelProfile.url() == null || modelProfile.url().isBlank()) {
			return Map.of();
		}
		final var cacheKey = modelProfile.category() + "|" + modelProfile.url() + "|" + modelProfile.apiKey();
		if (forceReload) {
			final var models = this.loadModels(modelProfile);
			this.resolvedModels.put(cacheKey, models);
			return models;
		}
		return this.resolvedModels.computeIfAbsent(cacheKey, key -> this.loadModels(modelProfile));
	}

	private Map<String, AnthropicModel> loadModels(final ModelProfile modelProfile) {
		final var base = stripMessagesSuffix(modelProfile.url());
		final var path = modelsPath(modelProfile.url());
		try {
			var requestSpec = this.restClientBuilder.baseUrl(base).build()
					.method(HttpMethod.GET)
					.uri(path)
					.header("anthropic-version", this.getAnthropicDialect().anthropicVersion(modelProfile));
			if (modelProfile.apiKey() != null && !modelProfile.apiKey().isBlank()) {
				requestSpec = requestSpec.header("x-api-key", modelProfile.apiKey());
			}
			requestSpec = this.proxyAuthorizationSupport.apply(requestSpec);
			final var response = requestSpec.exchange((clientRequest, clientResponse) -> {
				if (clientResponse.getStatusCode().isError()) {
					LOGGER.warn("Error response from model listing: {} {}", clientResponse.getStatusCode(), new String(clientResponse.getBody().readAllBytes(), StandardCharsets.UTF_8));
					return null;
				}
				return this.objectMapper.readValue(clientResponse.getBody(), AnthropicModelListResponse.class);
			});
			if (response == null || response.data() == null) {
				return Map.of();
			}
			final Map<String, AnthropicModel> models = new LinkedHashMap<>();
			for (final var model : response.data()) {
				if (model != null && model.id() != null && !model.id().isBlank()) {
					models.put(model.id(), model);
				}
			}
			return Collections.unmodifiableMap(models);
		}
		catch (final RuntimeException e) {
			LOGGER.warn("Failed to load Anthropic model listing from {}", modelProfile.url(), e);
			return Map.of();
		}
	}

	private AnthropicModel findModel(final ModelProfile modelProfile, final String modelName) {
		if (modelName == null || modelName.isBlank()) {
			return null;
		}
		return this.resolveModels(modelProfile, false).get(modelName);
	}

	@Override
	public ModelProvider getModelProvider() {
		return ModelProvider.ANTHROPIC;
	}

	@Override
	public boolean supportsToolCalling(final ModelCategory modelCategory) {
		final var modelProfile = this.modelProfileService.getModelProfile(modelCategory.getModelProfile(), modelCategory);
		return this.supportsToolCalling(modelProfile);
	}

	@Override
	public boolean supportsToolCalling(final ModelProfile modelProfile) {
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.ANTHROPIC) {
			return false;
		}
		return supportsToolCalling(this.findModel(modelProfile, this.getPrimaryModelName(modelProfile)));
	}

	/**
	 * Determines tool calling support from model capabilities. Unknown models or
	 * missing capability information skip the check and are assumed to support
	 * tool calling.
	 */
	static boolean supportsToolCalling(final AnthropicModel model) {
		if (model == null) {
			return true;
		}
		final var capabilities = model.capabilities();
		if (capabilities == null) {
			return true;
		}
		return capabilities.isToolCallingSupported();
	}

	/**
	 * Determines extended thinking support from model capabilities. Unknown
	 * models or missing capability information skip the check and are assumed to
	 * support thinking.
	 */
	static boolean supportsExtendedThinking(final AnthropicModel model) {
		if (model == null) {
			return true;
		}
		final var capabilities = model.capabilities();
		if (capabilities == null) {
			return true;
		}
		return capabilities.isExtendedThinkingSupported();
	}

	AnthropicMessageRequest createRequest(final Conversation conversation, final ModelProfile modelProfile, final boolean streaming, final ToolCallData toolCallData) {
		final var modelName = this.getPrimaryModelName(modelProfile);
		if (modelName == null || modelName.isBlank()) {
			throw new IllegalStateException("No model name resolved for Anthropic profile " + modelProfile.category());
		}
		if (modelProfile.maxTokens() <= 0) {
			throw new IllegalStateException("maxTokens must be greater than zero for Anthropic profile " + modelProfile.category());
		}
		final var systemBuilder = new StringBuilder();
		final var messages = this.createMessages(conversation, systemBuilder);
		final var request = new AnthropicMessageRequest(
				modelName,
				modelProfile.maxTokens(),
				systemBuilder.isEmpty() ? null : systemBuilder.toString(),
				messages,
				Double.valueOf(modelProfile.temperature()),
				Double.valueOf(modelProfile.topP()),
				null,
				Boolean.valueOf(streaming),
				toolCallData != null && toolCallData.hasTools() ? this.createTools(toolCallData.tools()) : null,
				toolCallData != null && toolCallData.hasTools() ? this.createToolChoice(toolCallData) : null,
				this.createThinkingConfig(modelProfile));
		return request;
	}

	List<AnthropicMessage> createMessages(final Conversation conversation, final StringBuilder systemCollector) {
		final List<AnthropicMessage> messages = new ArrayList<>();
		final List<AnthropicContentBlock> pendingToolResults = new ArrayList<>();
		for (final Turn turn : conversation.getConversation()) {
			switch (turn.type()) {
				case SYSTEM: {
					final var text = turn.text() != null ? turn.text() : "";
					if (!text.isBlank()) {
						if (!systemCollector.isEmpty()) {
							systemCollector.append("\n\n");
						}
						systemCollector.append(text);
					}
					break;
				}
				case USER: {
					this.flushToolResults(messages, pendingToolResults);
					messages.add(AnthropicMessage.user(turn.text() != null ? turn.text() : ""));
					break;
				}
				case ASSISTANT: {
					this.flushToolResults(messages, pendingToolResults);
					messages.add(this.createAssistantMessage(turn));
					break;
				}
				case TOOL: {
					final var text = turn.text() != null && !turn.text().isBlank() ? turn.text() : " ";
					pendingToolResults.add(AnthropicContentBlock.toolResult(turn.toolCallId(), text));
					break;
				}
			}
		}
		this.flushToolResults(messages, pendingToolResults);
		return messages;
	}

	private AnthropicMessage createAssistantMessage(final Turn turn) {
		final List<Object> blocks = new ArrayList<>();
		for (final var outputItem : turn.outputItems()) {
			final var type = outputItem.get("type") instanceof final String itemType ? itemType : null;
			if ("thinking".equals(type) || "redacted_thinking".equals(type)) {
				blocks.add(outputItem);
			}
		}
		final var text = turn.text() != null ? turn.text() : "";
		if (!text.isBlank() || (turn.toolCalls().isEmpty() && blocks.isEmpty())) {
			blocks.add(AnthropicContentBlock.text(text.isBlank() ? " " : text));
		}
		var toolCallIndex = 0;
		for (final var toolCall : turn.toolCalls()) {
			toolCallIndex++;
			final var id = toolCall.id() != null && !toolCall.id().isBlank() ? toolCall.id() : "toolu_lm_" + toolCallIndex;
			blocks.add(AnthropicContentBlock.toolUse(id, toolCall.name(), this.parseToolInput(toolCall.arguments())));
		}
		if (blocks.size() == 1 && blocks.getFirst() instanceof final AnthropicContentBlock block && "text".equals(block.type())) {
			return AnthropicMessage.assistant(block.text());
		}
		return AnthropicMessage.assistantBlocks(blocks);
	}

	private void flushToolResults(final List<AnthropicMessage> messages, final List<AnthropicContentBlock> pendingToolResults) {
		if (pendingToolResults.isEmpty()) {
			return;
		}
		messages.add(AnthropicMessage.userBlocks(List.copyOf(pendingToolResults)));
		pendingToolResults.clear();
	}

	private List<AnthropicTool> createTools(final List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream()
				.map(toolDefinition -> new AnthropicTool(toolDefinition.name(), toolDefinition.description(), toolDefinition.parameters()))
				.toList();
	}

	AnthropicToolChoice createToolChoice(final ToolCallData toolCallData) {
		final var parallel = toolCallData.parallelToolCalls();
		final Boolean disableParallelToolUse = parallel == null ? null : Boolean.valueOf(!parallel);
		if (toolCallData.forcedToolName() != null && !toolCallData.forcedToolName().isBlank()) {
			return new AnthropicToolChoice("tool", toolCallData.forcedToolName(), disableParallelToolUse);
		}
		final var toolChoice = toolCallData.toolChoice();
		if (toolChoice == null) {
			return disableParallelToolUse == null ? null : new AnthropicToolChoice("auto", null, disableParallelToolUse);
		}
		return switch (toolChoice) {
			case ToolCallData.TOOL_CHOICE_REQUIRED -> new AnthropicToolChoice("any", null, disableParallelToolUse);
			case ToolCallData.TOOL_CHOICE_NONE -> new AnthropicToolChoice("none", null, disableParallelToolUse);
			default -> new AnthropicToolChoice("auto", null, disableParallelToolUse);
		};
	}

	AnthropicThinkingConfig createThinkingConfig(final ModelProfile modelProfile) {
		final var budgetTokens = this.getAnthropicDialect().thinkingBudgetTokens(modelProfile);
		if (budgetTokens == null) {
			return null;
		}
		if (!supportsExtendedThinking(this.findModel(modelProfile, this.getPrimaryModelName(modelProfile)))) {
			LOGGER.debug("Model {} does not advertise extended thinking support - thinking will be omitted.", this.getPrimaryModelName(modelProfile));
			return null;
		}
		final var maxTokens = modelProfile.maxTokens();
		final var clamped = Math.min(budgetTokens.intValue(), maxTokens - 1);
		if (clamped < AnthropicDialect.MIN_THINKING_BUDGET_TOKENS) {
			LOGGER.debug("Thinking budget {} does not fit maxTokens {} - thinking will be omitted.", budgetTokens, maxTokens);
			return null;
		}
		return AnthropicThinkingConfig.enabled(Integer.valueOf(clamped));
	}

	AnthropicMessageResponse readStream(final InputStream inputStream, final Consumer<Chunk> chunkConsumer) throws IOException {
		final List<StreamBlock> blocks = new ArrayList<>();
		String id = null;
		String model = null;
		String stopReason = null;
		String stopSequence = null;
		AnthropicUsage usage = null;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith(":") || line.startsWith("event:")) {
					continue;
				}
				if (line.startsWith("data:")) {
					line = line.substring(5).trim();
				}
				if (line.isEmpty()) {
					continue;
				}

				final AnthropicStreamEvent event;
				try {
					event = this.objectMapper.readValue(line, AnthropicStreamEvent.class);
				}
				catch (final JacksonException e) {
					LOGGER.warn("Streamed event is not parseable: {}", line, e);
					continue;
				}
				if (event.type() == null) {
					continue;
				}

				switch (event.type()) {
					case AnthropicStreamEvent.EVENT_MESSAGE_START:
						if (event.message() != null) {
							id = event.message().id() != null ? event.message().id() : id;
							model = event.message().model() != null ? event.message().model() : model;
							usage = event.message().usage() != null ? event.message().usage().mergedWith(usage) : usage;
						}
						break;
					case AnthropicStreamEvent.EVENT_CONTENT_BLOCK_START:
						this.startBlock(blocks, event);
						break;
					case AnthropicStreamEvent.EVENT_CONTENT_BLOCK_DELTA:
						this.applyDelta(blocks, event, chunkConsumer);
						break;
					case AnthropicStreamEvent.EVENT_MESSAGE_DELTA:
						if (event.delta() != null) {
							stopReason = event.delta().stopReason() != null ? event.delta().stopReason() : stopReason;
							stopSequence = event.delta().stopSequence() != null ? event.delta().stopSequence() : stopSequence;
						}
						usage = event.usage() != null ? usage.mergedWith(event.usage()) : usage;
						break;
					case AnthropicStreamEvent.EVENT_ERROR:
						final var errorMessage = event.error() != null && event.error().message() != null ? event.error().message() : "unknown error";
						LOGGER.warn("Error event in streamed Anthropic response: {}", errorMessage);
						throw new IllegalStateException("Error in streamed Anthropic response: " + errorMessage);
					case AnthropicStreamEvent.EVENT_MESSAGE_STOP:
						return this.aggregate(id, model, stopReason, stopSequence, usage, blocks);
					default:
						break; // ping, content_block_stop, and unknown events
				}
			}
		}
		return this.aggregate(id, model, stopReason, stopSequence, usage, blocks);
	}

	private void startBlock(final List<StreamBlock> blocks, final AnthropicStreamEvent event) {
		final var block = this.ensureBlock(blocks, event.index());
		final var contentBlock = event.contentBlock();
		if (contentBlock == null) {
			return;
		}
		block.type = contentBlock.type();
		if (contentBlock.id() != null && !contentBlock.id().isBlank()) {
			block.id = contentBlock.id();
		}
		if (contentBlock.name() != null && !contentBlock.name().isBlank()) {
			block.name = contentBlock.name();
		}
		if (contentBlock.text() != null) {
			block.text.append(contentBlock.text());
		}
		if (contentBlock.thinking() != null) {
			block.thinking.append(contentBlock.thinking());
		}
		if (contentBlock.data() != null) {
			block.data = contentBlock.data();
		}
	}

	private void applyDelta(final List<StreamBlock> blocks, final AnthropicStreamEvent event, final Consumer<Chunk> chunkConsumer) {
		final var delta = event.delta();
		if (delta == null || delta.type() == null) {
			return;
		}
		final var block = this.ensureBlock(blocks, event.index());
		switch (delta.type()) {
			case Delta.DELTA_TYPE_TEXT:
				if (delta.text() != null && !delta.text().isEmpty()) {
					block.text.append(delta.text());
					chunkConsumer.accept(new Chunk(delta.text(), null));
				}
				break;
			case Delta.DELTA_TYPE_THINKING:
				if (delta.thinking() != null && !delta.thinking().isEmpty()) {
					block.thinking.append(delta.thinking());
					chunkConsumer.accept(new Chunk(null, delta.thinking()));
				}
				break;
			case Delta.DELTA_TYPE_INPUT_JSON:
				if (delta.partialJson() != null) {
					block.inputJson.append(delta.partialJson());
				}
				break;
			case Delta.DELTA_TYPE_SIGNATURE:
				if (delta.signature() != null) {
					block.signature = delta.signature();
				}
				break;
			default:
				break;
		}
	}

	private StreamBlock ensureBlock(final List<StreamBlock> blocks, final Integer index) {
		final var target = index != null ? index.intValue() : blocks.size();
		while (blocks.size() <= target) {
			blocks.add(new StreamBlock());
		}
		return blocks.get(target);
	}

	private AnthropicMessageResponse aggregate(final String id, final String model, final String stopReason, final String stopSequence, final AnthropicUsage usage,
			final List<StreamBlock> blocks) {
		final List<AnthropicContentBlock> content = new ArrayList<>();
		for (final var block : blocks) {
			final var type = block.type != null ? block.type : "text";
			switch (type) {
				case "text":
					if (!block.text.isEmpty()) {
						content.add(AnthropicContentBlock.text(block.text.toString()));
					}
					break;
				case "tool_use":
					content.add(AnthropicContentBlock.toolUse(block.id, block.name, this.parseToolInput(block.inputJson.toString())));
					break;
				case "thinking":
					if (!block.thinking.isEmpty() || block.signature != null) {
						content.add(AnthropicContentBlock.thinking(block.thinking.toString(), block.signature));
					}
					break;
				case "redacted_thinking":
					if (block.data != null) {
						content.add(AnthropicContentBlock.redactedThinking(block.data));
					}
					break;
				default:
					LOGGER.debug("Ignoring unsupported streamed content block type: {}", type);
					break;
			}
		}
		return new AnthropicMessageResponse(id, "message", "assistant", model, List.copyOf(content), stopReason, stopSequence, usage);
	}

	Completion mapResponse(final AnthropicMessageResponse response, final ModelProfile modelProfile, final LocalDateTime start, final Conversation conversation) {
		final var contentBuilder = new StringBuilder();
		final var reasoningBuilder = new StringBuilder();
		final List<ToolCall> toolCalls = new ArrayList<>();
		final List<Map<String, Object>> outputItems = new ArrayList<>();

		for (final var block : response.content() == null ? List.<AnthropicContentBlock>of() : response.content()) {
			if (block == null || block.type() == null) {
				continue;
			}
			switch (block.type()) {
				case "text":
					if (block.text() != null) {
						contentBuilder.append(block.text());
					}
					break;
				case "thinking":
					if (block.thinking() != null && !block.thinking().isEmpty()) {
						if (!reasoningBuilder.isEmpty()) {
							reasoningBuilder.append("\n");
						}
						reasoningBuilder.append(block.thinking());
					}
					final var thinkingItem = new LinkedHashMap<String, Object>();
					thinkingItem.put("type", "thinking");
					thinkingItem.put("thinking", block.thinking() != null ? block.thinking() : "");
					thinkingItem.put("signature", block.signature() != null ? block.signature() : "");
					outputItems.add(Map.copyOf(thinkingItem));
					break;
				case "redacted_thinking":
					if (block.data() != null) {
						final Map<String, Object> redactedItem = new LinkedHashMap<String, Object>();
						redactedItem.put("type", "redacted_thinking");
						redactedItem.put("data", block.data());
						outputItems.add(Map.copyOf(redactedItem));
					}
					break;
				case "tool_use":
					toolCalls.add(new ToolCall(block.id(), "tool_use", block.name(), this.serializeToolInput(block.input())));
					break;
				default:
					LOGGER.debug("Ignoring unsupported response content block type: {}", block.type());
					break;
			}
		}

		final var content = contentBuilder.toString();
		final var reasoning = reasoningBuilder.toString();
		return new Completion(content, reasoning, this.mapFinishReason(response.stopReason(), toolCalls),
				this.createCompletionStatistics(modelProfile, start, response.usage(), conversation, content, reasoning),
				List.copyOf(toolCalls), List.copyOf(outputItems));
	}

	CompletionFinishReason mapFinishReason(final String stopReason, final List<ToolCall> toolCalls) {
		if (!toolCalls.isEmpty()) {
			return CompletionFinishReason.TOOL_CALLS;
		}
		if (stopReason == null) {
			return CompletionFinishReason.DONE;
		}
		return switch (stopReason) {
			case AnthropicMessageResponse.STOP_REASON_MAX_TOKENS -> CompletionFinishReason.TOKEN_LIMIT_REACHED;
			case AnthropicMessageResponse.STOP_REASON_REFUSAL -> CompletionFinishReason.CONTENT_FILTERED;
			default -> CompletionFinishReason.DONE; // end_turn, stop_sequence, pause_turn
		};
	}

	private CompletionStatistics createCompletionStatistics(final ModelProfile modelProfile, final LocalDateTime start, final AnthropicUsage usage,
			final Conversation conversation, final String content, final String reasoning) {
		int inTokens = 0;
		int cachedInTokens = 0;
		int outTokens = 0;
		int reasoningOutTokens = 0;

		if (usage != null) {
			inTokens = usage.inputTokens() != null ? usage.inputTokens().intValue() : 0;
			cachedInTokens = usage.cacheReadInputTokens() != null ? usage.cacheReadInputTokens().intValue() : 0;
			inTokens += usage.cacheCreationInputTokens() != null ? usage.cacheCreationInputTokens().intValue() : 0;
			outTokens = usage.outputTokens() != null ? usage.outputTokens().intValue() : 0;
		}
		if (reasoningOutTokens <= 0 && !reasoning.isBlank()) {
			reasoningOutTokens = this.tokenizerResolver.count(reasoning, modelProfile);
			outTokens = Math.max(0, outTokens - reasoningOutTokens);
		}
		if (inTokens <= 0) {
			inTokens = this.tokenizerResolver.count(this.createPromptForCounting(conversation), modelProfile);
		}
		if (outTokens <= 0 && !content.isBlank()) {
			outTokens = this.tokenizerResolver.count(content, modelProfile);
		}

		var cost = BigDecimal.ZERO;
		cost = cost.add(BigDecimal.valueOf(inTokens).multiply(modelProfile.costPerInToken()));
		cost = cost.add(BigDecimal.valueOf(cachedInTokens).multiply(modelProfile.costPerCachedInToken()));
		cost = cost.add(BigDecimal.valueOf(outTokens).multiply(modelProfile.costPerOutToken()));
		cost = cost.add(BigDecimal.valueOf(reasoningOutTokens).multiply(modelProfile.costPerReasoningOutToken()));
		final var statistics = new CompletionStatistics(1, Duration.between(start, LocalDateTime.now()), inTokens, cachedInTokens, outTokens, reasoningOutTokens, cost, false);
		LOGGER.debug("{}", statistics);
		return statistics;
	}

	private String createPromptForCounting(final Conversation conversation) {
		final var builder = new StringBuilder();
		for (final var turn : conversation.getConversation()) {
			builder.append(turn.type().name());
			builder.append(':');
			builder.append(turn.text() != null ? turn.text() : "");
			builder.append('\n');
			for (final var toolCall : turn.toolCalls()) {
				builder.append(toolCall.name());
				builder.append(':');
				builder.append(toolCall.arguments());
				builder.append('\n');
			}
		}
		return builder.toString();
	}

	private String getPrimaryModelName(final ModelProfile modelProfile) {
		if (modelProfile.modelName() != null && !modelProfile.modelName().isBlank()) {
			return modelProfile.modelName();
		}
		return this.getModelNames(modelProfile, false).stream().findFirst().orElse("");
	}

	private String getErrorMessage(final String errorBody) {
		try {
			final var errorResponse = this.objectMapper.readValue(errorBody, AnthropicErrorResponse.class);
			if (errorResponse != null && errorResponse.error() != null && errorResponse.error().message() != null && !errorResponse.error().message().isBlank()) {
				return errorResponse.error().message();
			}
		}
		catch (final JacksonException e) {
			LOGGER.debug("Failed to parse error response body", e);
		}
		return errorBody;
	}

	private Map<String, Object> parseToolInput(final String arguments) {
		if (arguments == null || arguments.isBlank()) {
			return Map.of();
		}
		try {
			final var parsed = this.objectMapper.readValue(arguments, TOOL_INPUT_TYPE);
			return parsed == null ? Map.of() : parsed;
		}
		catch (final JacksonException e) {
			LOGGER.warn("Tool call arguments are not a valid JSON object, sending empty input: {}", arguments);
			return Map.of();
		}
	}

	private String serializeToolInput(final Map<String, Object> input) {
		if (input == null) {
			return null;
		}
		try {
			return this.objectMapper.writeValueAsString(input);
		}
		catch (final JacksonException e) {
			throw new IllegalStateException("Cannot serialize tool call arguments", e);
		}
	}

	/**
	 * Normalizes the configured base URL by removing a trailing
	 * {@code /v1/messages} endpoint suffix (the request path is appended separately).
	 */
	static String stripMessagesSuffix(final String url) {
		if (url != null && url.endsWith(MESSAGES_PATH)) {
			final var stripped = url.substring(0, url.length() - MESSAGES_PATH.length());
			return stripped.isEmpty() ? url : stripped;
		}
		return url;
	}

	/**
	 * Resolves the messages endpoint path relative to the (suffix-normalized)
	 * configured base URL.
	 */
	static String messagesPath(final String url) {
		if (url == null) {
			return MESSAGES_PATH;
		}
		if (url.endsWith("/v1/") || url.endsWith("/v1")) {
			return "/messages";
		}
		return MESSAGES_PATH;
	}

	/**
	 * Resolves the models listing path relative to the (suffix-normalized)
	 * configured base URL.
	 */
	static String modelsPath(final String url) {
		if (url == null) {
			return "/v1/models";
		}
		if (url.endsWith("/v1/") || url.endsWith("/v1")) {
			return "/models";
		}
		return "/v1/models";
	}

	private AnthropicDialect getAnthropicDialect() {
		return this.anthropicDialect == null ? DEFAULT_ANTHROPIC_DIALECT : this.anthropicDialect;
	}

	private static final class StreamBlock {

		private String type;
		private String id;
		private String name;
		private String signature;
		private String data;
		private final StringBuilder text = new StringBuilder();
		private final StringBuilder thinking = new StringBuilder();
		private final StringBuilder inputJson = new StringBuilder();
	}

	@Override
	public String toString() {
		return "AnthropicClient []";
	}
}
