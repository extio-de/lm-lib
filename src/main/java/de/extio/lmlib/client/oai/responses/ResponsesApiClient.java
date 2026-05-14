package de.extio.lmlib.client.oai.responses;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.CompletionStatistics;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.ToolCallData;
import de.extio.lmlib.client.ToolCall;
import de.extio.lmlib.client.ToolDefinition;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;
import de.extio.lmlib.profile.ModelProfileService;
import de.extio.lmlib.token.TokenizerResolver;

public class ResponsesApiClient implements Client{

	private static final Logger LOGGER = LoggerFactory.getLogger(ResponsesApiClient.class);

	private static final String ASSISTANT_PHASE_COMMENTARY = "commentary";

	private static final String ASSISTANT_PHASE_FINAL_ANSWER = "final_answer";

	private static final int TOKEN_MARGIN = 15;

	private static final OpenAiResponsesApiDialect DEFAULT_DIALECT = new OpenAiResponsesApiDialect() {
	};

	@Autowired
	@Qualifier("lmLibRestClientBuilder")
	private RestClient.Builder restClientBuilder;

	@Autowired
	private TokenizerResolver tokenizerResolver;

	@Autowired
	private ModelProfileService modelProfileService;

	@Autowired(required = false)
	private OpenAiResponsesApiDialect openAiResponsesApiDialect;

	private final Map<String, List<String>> resolvedModelNames = new ConcurrentHashMap<>();

	private final ObjectMapper objectMapper;

	public ResponsesApiClient() {
		this.objectMapper = JsonMapper.builder()
				.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
				.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
				.configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, false)
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false)
				.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
				.changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))
				.enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
				.enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
				.enable(JsonReadFeature.ALLOW_MISSING_VALUES)
				.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
				.enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
				.enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
				.enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
				.enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
				.enable(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
				.enable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
				.enable(JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)
				.enable(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)
				.build();
	}

	@Override
	public Completion conversation(final ModelCategory modelCategory, final Conversation conversation, final ToolCallData toolCallData, final boolean skipCache) {
		return this.streamConversation(modelCategory, conversation, null, toolCallData, skipCache);
	}

	@Override
	public Completion conversation(final ModelProfile modelProfile, final Conversation conversation, final ToolCallData toolCallData, final boolean skipCache) {
		return this.streamConversation(modelProfile, conversation, null, toolCallData, skipCache);
	}

	@Override
	public Completion streamConversation(final ModelCategory modelCategory, final Conversation conversation, final Consumer<Chunk> chunkConsumer, final ToolCallData toolCallData, final boolean skipCache) {
		final var resolvedModelCategory = modelCategory == null ? ModelCategory.MEDIUM : modelCategory;
		final var modelProfile = this.modelProfileService.getModelProfile(resolvedModelCategory.getModelProfile(), resolvedModelCategory);
		return this.streamConversation(modelProfile, conversation, chunkConsumer, toolCallData, skipCache);
	}

	@Override
	public Completion streamConversation(final ModelProfile modelProfile, final Conversation conversation, final Consumer<Chunk> chunkConsumer, final ToolCallData toolCallData, final boolean skipCache) {
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.OAI_RESPONSES) {
			throw new IllegalArgumentException("Invalid ModelProfile");
		}
		return this.requestCompletion(conversation, modelProfile, chunkConsumer, toolCallData);
	}

	@Override
	public List<String> getModelNames(final ModelProfile modelProfile, final boolean forceReload) {
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.OAI_RESPONSES) {
			return List.of();
		}
		if (modelProfile.modelName() != null && !modelProfile.modelName().isBlank()) {
			return List.of(modelProfile.modelName());
		}
		if (modelProfile.url() == null || modelProfile.url().isBlank()) {
			return List.of();
		}

		final var cacheKey = modelProfile.category() + "|" + modelProfile.url() + "|" + modelProfile.apiKey();
		if (forceReload) {
			final var modelNames = this.loadModelNames(modelProfile);
			this.resolvedModelNames.put(cacheKey, modelNames);
			return modelNames;
		}
		return this.resolvedModelNames.computeIfAbsent(cacheKey, key -> this.loadModelNames(modelProfile));
	}

	@Override
	public ModelProvider getModelProvider() {
		return ModelProvider.OAI_RESPONSES;
	}

	@Override
	public boolean supportsToolCalling(final ModelCategory modelCategory) {
		final var resolvedModelCategory = modelCategory == null ? ModelCategory.MEDIUM : modelCategory;
		final var modelProfile = this.modelProfileService.getModelProfile(resolvedModelCategory.getModelProfile(), resolvedModelCategory);
		return this.supportsToolCalling(modelProfile);
	}

	@Override
	public boolean supportsToolCalling(final ModelProfile modelProfile) {
		return modelProfile != null && modelProfile.modelProvider() == ModelProvider.OAI_RESPONSES;
	}

	private Completion requestCompletion(final Conversation conversation, final ModelProfile modelProfile, final Consumer<Chunk> chunkConsumer, final ToolCallData toolCallData) {
		final var inputTextBuilder = new StringBuilder();
		final var inputItems = this.createInputItems(conversation, modelProfile, inputTextBuilder);
		final var request = this.createResponseRequest(inputItems, modelProfile, chunkConsumer != null, toolCallData);

		final String requestBody;
		try {
			requestBody = this.objectMapper.writeValueAsString(request);
		}
		catch (final JacksonException e) {
			throw new IllegalStateException("Cannot serialize request body", e);
		}

		LOGGER.debug("Requesting response at {}", modelProfile.url());
		final var start = LocalDateTime.now();
		var requestSpec = this.restClientBuilder.baseUrl(modelProfile.url()).build()
				.method(HttpMethod.POST)
				.uri("/v1/responses")
				.header("Content-Type", "application/json");
		if (modelProfile.apiKey() != null && !modelProfile.apiKey().isBlank()) {
			requestSpec = requestSpec.header("Authorization", "Bearer " + modelProfile.apiKey());
		}

		return requestSpec.body(requestBody).exchange((clientRequest, clientResponse) -> {
			if (clientResponse.getStatusCode().isError()) {
				final var errorBody = new String(clientResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
				final var errorMessage = this.getErrorMessage(errorBody);
				LOGGER.warn("Error response from server: {} {}", clientResponse.getStatusCode(), errorMessage);
				throw new IllegalStateException("Error response from server: " + clientResponse.getStatusCode() + " " + errorMessage);
			}

			if (chunkConsumer == null) {
				return this.parseCompletion(clientResponse.getBody(), modelProfile, start, inputTextBuilder.toString());
			}
			return this.parseStreamingCompletion(clientResponse.getBody(), modelProfile, start, inputTextBuilder.toString(), chunkConsumer);
		});
	}

	private Map<String, Object> createResponseRequest(final List<Map<String, Object>> inputItems, final ModelProfile modelProfile, final boolean streaming, final ToolCallData toolCallData) {
		final var dialect = this.responsesDialect();
		final var store = dialect.store(modelProfile);
		final var request = new LinkedHashMap<String, Object>();
		request.put("model", this.getPrimaryModelName(modelProfile));
		request.put("input", inputItems);
		request.put("store", store);
		request.put("stream", streaming);
		request.put("temperature", modelProfile.temperature());
		request.put("top_p", modelProfile.topP());
		if (modelProfile.maxTokens() > 0) {
			request.put("max_output_tokens", modelProfile.maxTokens());
		}
		final var reasoning = this.createReasoningRequest(dialect.reasoning(modelProfile));
		if (reasoning != null) {
			request.put("reasoning", reasoning);
		}
		final var include = this.createIncludeList(modelProfile, store);
		if (!include.isEmpty()) {
			request.put("include", include);
		}
		final var background = dialect.background(modelProfile);
		if (background != null) {
			request.put("background", background);
		}
		final var maxToolCalls = dialect.maxToolCalls(modelProfile);
		if (maxToolCalls != null) {
			request.put("max_tool_calls", maxToolCalls);
		}
		final var metadata = dialect.metadata(modelProfile);
		if (metadata != null && !metadata.isEmpty()) {
			request.put("metadata", metadata);
		}
		final var promptCacheKey = dialect.promptCacheKey(modelProfile);
		if (promptCacheKey != null && !promptCacheKey.isBlank()) {
			request.put("prompt_cache_key", promptCacheKey);
		}
		final var promptCacheRetention = dialect.promptCacheRetention(modelProfile);
		if (promptCacheRetention != null) {
			request.put("prompt_cache_retention", promptCacheRetention);
		}
		final var safetyIdentifier = dialect.safetyIdentifier(modelProfile);
		if (safetyIdentifier != null && !safetyIdentifier.isBlank()) {
			request.put("safety_identifier", safetyIdentifier);
		}
		final var serviceTier = dialect.serviceTier(modelProfile);
		if (serviceTier != null) {
			request.put("service_tier", serviceTier);
		}
		final var text = this.createTextRequest(dialect.text(modelProfile));
		if (text != null) {
			request.put("text", text);
		}
		final var topLogprobs = dialect.topLogprobs(modelProfile);
		if (topLogprobs != null) {
			request.put("top_logprobs", topLogprobs);
		}
		final var truncation = dialect.truncation(modelProfile);
		if (truncation != null) {
			request.put("truncation", truncation);
		}
		final var streamOptions = this.createStreamOptionsRequest(dialect.streamOptions(modelProfile));
		if (streaming && streamOptions != null) {
			request.put("stream_options", streamOptions);
		}
		if (toolCallData != null && toolCallData.hasTools()) {
			request.put("tools", this.createTools(toolCallData.tools()));
			final var toolChoice = this.createToolChoice(toolCallData);
			if (toolChoice != null) {
				request.put("tool_choice", toolChoice);
			}
			if (toolCallData.parallelToolCalls() != null) {
				request.put("parallel_tool_calls", toolCallData.parallelToolCalls());
			}
		}
		return request;
	}

	private List<Map<String, Object>> createInputItems(final Conversation conversation, final ModelProfile modelProfile, final StringBuilder inputTextBuilder) {
		final var inputItems = new ArrayList<Map<String, Object>>();
		var tokens = 0;
		final var maxLength = Math.max(0, modelProfile.maxContextLength() - modelProfile.maxTokens() - TOKEN_MARGIN);

		for (final Turn turn : conversation.getConversation()) {
			switch (turn.type()) {
				case SYSTEM -> {
					if (this.addInputMessageItem("developer", turn.text(), null, inputItems, inputTextBuilder, modelProfile, maxLength, tokens)) {
						return inputItems;
					}
					tokens = this.countTokens(inputTextBuilder.toString(), modelProfile);
				}
				case USER -> {
					if (this.addInputMessageItem("user", turn.text(), null, inputItems, inputTextBuilder, modelProfile, maxLength, tokens)) {
						return inputItems;
					}
					tokens = this.countTokens(inputTextBuilder.toString(), modelProfile);
				}
				case ASSISTANT -> {
					if (!turn.outputItems().isEmpty()) {
						if (this.addAssistantOutputItems(turn.outputItems(), inputItems, inputTextBuilder, modelProfile, maxLength)) {
							return inputItems;
						}
						tokens = this.countTokens(inputTextBuilder.toString(), modelProfile);
						break;
					}
					if (turn.text() != null && !turn.text().isBlank()) {
						if (this.addInputMessageItem("assistant", turn.text(), this.resolveAssistantPhase(turn.phase(), turn.toolCalls()), inputItems, inputTextBuilder, modelProfile, maxLength, tokens)) {
							return inputItems;
						}
						tokens = this.countTokens(inputTextBuilder.toString(), modelProfile);
					}
					for (final var toolCall : turn.toolCalls()) {
						final var outputItem = new LinkedHashMap<String, Object>();
						outputItem.put("type", "function_call");
						outputItem.put("call_id", toolCall.id());
						outputItem.put("name", toolCall.name());
						outputItem.put("arguments", toolCall.arguments());
						outputItem.put("status", "completed");
						inputItems.add(outputItem);
						this.appendInputItemText(inputTextBuilder, outputItem);
						tokens = this.countTokens(inputTextBuilder.toString(), modelProfile);
						if (tokens > maxLength && maxLength > 0) {
							LOGGER.warn("Prompt too long: {} tokens / {} max. Conversation will be cut!", tokens, maxLength);
							return inputItems;
						}
					}
				}
				case TOOL -> {
					final var toolOutput = turn.text() == null ? "" : turn.text();
					var normalizedOutput = toolOutput;
					if (maxLength > 0) {
						final var currentTokens = this.countTokens(inputTextBuilder.toString(), modelProfile);
						final var remaining = maxLength - currentTokens;
						if (remaining <= 0) {
							LOGGER.warn("Prompt too long: {} tokens / {} max. Conversation will be cut!", currentTokens, maxLength);
							return inputItems;
						}
						normalizedOutput = this.trimToTokenBudget(toolOutput, remaining, modelProfile);
					}
					final var item = new LinkedHashMap<String, Object>();
					item.put("type", "function_call_output");
					item.put("call_id", turn.toolCallId());
					item.put("output", normalizedOutput);
					inputItems.add(item);
					this.appendInputItemText(inputTextBuilder, item);
					tokens = this.countTokens(inputTextBuilder.toString(), modelProfile);
					if (tokens > maxLength) {
						LOGGER.warn("Prompt too long: {} tokens / {} max. Conversation will be cut!", tokens, maxLength);
						return inputItems;
					}
				}
			}
		}

		return inputItems;
	}

	private boolean addInputMessageItem(final String role, final String text, final String phase, final List<Map<String, Object>> inputItems, final StringBuilder inputTextBuilder, final ModelProfile modelProfile, final int maxLength, final int currentTokens) {
		var normalizedText = text == null ? "" : text;
		if (maxLength > 0) {
			final var remaining = maxLength - currentTokens;
			if (remaining <= 0) {
				LOGGER.warn("Prompt too long: {} tokens / {} max. Conversation will be cut!", currentTokens, maxLength);
				return true;
			}
			normalizedText = this.trimToTokenBudget(normalizedText, remaining, modelProfile);
		}
		final Map<String, Object> item;
		if ("assistant".equals(role)) {
			item = this.createAssistantMessageInputItem(normalizedText, phase);
		}
		else {
			final var easyInputMessage = new LinkedHashMap<String, Object>();
			easyInputMessage.put("role", role);
			easyInputMessage.put("content", normalizedText);
			item = easyInputMessage;
		}
		inputItems.add(item);
		this.appendInputText(inputTextBuilder, normalizedText);
		return this.countTokens(inputTextBuilder.toString(), modelProfile) > maxLength && maxLength > 0;
	}

	private Map<String, Object> createAssistantMessageInputItem(final String normalizedText, final String phase) {
		final var messageItem = new LinkedHashMap<String, Object>();
		messageItem.put("type", "message");
		messageItem.put("role", "assistant");
		messageItem.put("status", "completed");
		messageItem.put("content", List.of(Map.of("type", "output_text", "text", normalizedText)));
		if (phase != null && !phase.isBlank()) {
			messageItem.put("phase", phase);
		}
		return Map.copyOf(messageItem);
	}

	private boolean addAssistantOutputItems(final List<Map<String, Object>> outputItems, final List<Map<String, Object>> inputItems, final StringBuilder inputTextBuilder, final ModelProfile modelProfile, final int maxLength) {
		final var containsFunctionCalls = outputItems.stream().anyMatch(outputItem -> "function_call".equals(outputItem.get("type")));
		for (final var outputItem : outputItems) {
			final var normalizedOutputItem = this.normalizeAssistantOutputItemPhase(outputItem, containsFunctionCalls);
			inputItems.add(normalizedOutputItem);
			this.appendInputItemText(inputTextBuilder, normalizedOutputItem);
			final var tokens = this.countTokens(inputTextBuilder.toString(), modelProfile);
			if (tokens > maxLength && maxLength > 0) {
				LOGGER.warn("Prompt too long: {} tokens / {} max. Conversation will be cut!", tokens, maxLength);
				return true;
			}
		}
		return false;
	}

	private String trimToTokenBudget(final String text, final int remainingTokens, final ModelProfile modelProfile) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		final var textTokens = this.countTokens(text, modelProfile);
		if (textTokens <= remainingTokens) {
			return text;
		}
		var tokenized = this.tokenizerResolver.tokenize(text, modelProfile);
		if (remainingTokens <= 0 || tokenized.isEmpty()) {
			return "";
		}
		final var endIndex = Math.max(1, Math.min(tokenized.size(), remainingTokens));
		return this.tokenizerResolver.detokenize(tokenized.subList(0, endIndex), modelProfile);
	}

	private List<Map<String, Object>> createTools(final List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			final var tool = new LinkedHashMap<String, Object>();
			tool.put("type", "function");
			tool.put("name", toolDefinition.name());
			tool.put("description", toolDefinition.description());
			tool.put("parameters", toolDefinition.parameters());
			if (toolDefinition.strict() != null) {
				tool.put("strict", toolDefinition.strict());
			}
			return Map.copyOf(tool);
		}).toList();
	}

	private Object createToolChoice(final ToolCallData toolCallData) {
		if (toolCallData == null) {
			return null;
		}
		if (toolCallData.forcedToolName() != null && !toolCallData.forcedToolName().isBlank()) {
			final var toolChoice = new LinkedHashMap<String, Object>();
			toolChoice.put("type", "function");
			toolChoice.put("name", toolCallData.forcedToolName());
			return toolChoice;
		}
		if (toolCallData.toolChoice() != null && !toolCallData.toolChoice().isBlank()) {
			return toolCallData.toolChoice();
		}
		return null;
	}

	private Completion parseCompletion(final InputStream body, final ModelProfile modelProfile, final LocalDateTime start, final String prompt) throws IOException {
		final var response = this.objectMapper.readTree(body);
		final var output = response.path("output");
		final var responseText = this.extractAssistantMessageText(output);
		final var reasoningText = this.extractReasoningSummaryText(output);
		final var toolCalls = this.extractToolCalls(output);
		final var outputItems = this.extractOutputItems(output);
		final var finishReason = this.determineFinishReason(response, toolCalls);
		final var statistics = this.createCompletionStatistics(modelProfile, start, response.path("usage"), prompt, responseText, reasoningText);
		return new Completion(responseText, reasoningText, finishReason, statistics, toolCalls, outputItems);
	}

	private Completion parseStreamingCompletion(final InputStream body, final ModelProfile modelProfile, final LocalDateTime start, final String prompt, final Consumer<Chunk> chunkConsumer) throws IOException {
		var response = this.objectMapper.createObjectNode();
		final var responseText = new StringBuilder();
		final var reasoningText = new StringBuilder();
		final var toolCalls = new ArrayList<StreamingToolCall>();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith(":")) {
					continue;
				}
				if (line.startsWith("event:")) {
					continue;
				}
				if (line.startsWith("data:")) {
					line = line.substring(5).trim();
				}
				if (line.isEmpty()) {
					continue;
				}
				if ("[DONE]".equals(line)) {
					break;
				}

				final JsonNode event;
				try {
					event = this.objectMapper.readTree(line);
				}
				catch (final JacksonException e) {
					LOGGER.warn("Streamed chunk is not parseable: {}", line, e);
					continue;
				}

				final var eventType = this.text(event, "type");
				switch (eventType) {
					case "response.output_text.delta" -> {
						final var delta = this.text(event, "delta");
						if (!delta.isEmpty()) {
							responseText.append(delta);
							chunkConsumer.accept(new Chunk(delta, null));
						}
					}
					case "response.reasoning_text.delta" -> {
						final var delta = this.extractReasoningDelta(eventType, event);
						if (!delta.isEmpty()) {
							reasoningText.append(delta);
							chunkConsumer.accept(new Chunk(null, delta));
						}
					}
					case "response.output_item.added" -> this.mergeFunctionCallItem(toolCalls, event.path("item"), this.integerValue(event.path("output_index"), -1));
					case "response.output_item.done" -> this.mergeFunctionCallItem(toolCalls, event.path("item"), this.integerValue(event.path("output_index"), -1));
					case "response.function_call_arguments.delta" -> this.mergeFunctionCallArguments(toolCalls, this.integerValue(event.path("output_index"), -1), this.text(event, "delta"));
					case "response.function_call_arguments.done" -> this.setFunctionCallArguments(toolCalls, this.integerValue(event.path("output_index"), -1), this.text(event, "arguments"));
					case "response.completed" -> {
						if (event.path("response").isObject()) {
							response = (tools.jackson.databind.node.ObjectNode) event.path("response");
						}
					}
					case "response.failed", "error" -> throw new IllegalStateException(this.getEventErrorMessage(event));
					default -> {
						// Ignore unknown event types
						LOGGER.debug("Unhandled event type in streamed response: {}", eventType);
					}
				}
			}
		}

		if (!response.path("output").isArray()) {
			response.putArray("output");
		}

		final var completedText = responseText.isEmpty() ? this.extractAssistantMessageText(response.path("output")) : responseText.toString();
		final var completedReasoning = reasoningText.isEmpty() ? this.extractReasoningSummaryText(response.path("output")) : reasoningText.toString();
		final var completedToolCalls = this.extractToolCalls(response.path("output"));
		final var finalToolCalls = completedToolCalls.isEmpty() ? this.toToolCalls(toolCalls) : completedToolCalls;
		var outputItems = this.extractOutputItems(response.path("output"));
		if (outputItems.isEmpty()) {
			outputItems = this.createFallbackOutputItems(completedText, finalToolCalls);
		}
		final var finishReason = this.determineFinishReason(response, finalToolCalls);
		final var statistics = this.createCompletionStatistics(modelProfile, start, response.path("usage"), prompt, completedText, completedReasoning);
		return new Completion(completedText, completedReasoning, finishReason, statistics, finalToolCalls, outputItems);
	}

	private void mergeFunctionCallItem(final List<StreamingToolCall> toolCalls, final JsonNode item, final int outputIndex) {
		if (!"function_call".equals(this.text(item, "type"))) {
			return;
		}
		final var toolCall = this.ensureFunctionCall(toolCalls, outputIndex);
		final var callId = this.text(item, "call_id");
		if (!callId.isEmpty()) {
			toolCall.callId = callId;
		}
		final var name = this.text(item, "name");
		if (!name.isEmpty()) {
			toolCall.name = name;
		}
		final var arguments = this.text(item, "arguments");
		if (!arguments.isEmpty()) {
			toolCall.arguments = arguments;
		}
		toolCall.type = "function";
	}

	private void mergeFunctionCallArguments(final List<StreamingToolCall> toolCalls, final int outputIndex, final String delta) {
		if (delta == null || delta.isEmpty()) {
			return;
		}
		final var toolCall = this.ensureFunctionCall(toolCalls, outputIndex);
		toolCall.arguments = (toolCall.arguments == null ? "" : toolCall.arguments) + delta;
	}

	private void setFunctionCallArguments(final List<StreamingToolCall> toolCalls, final int outputIndex, final String arguments) {
		if (arguments == null || arguments.isEmpty()) {
			return;
		}
		this.ensureFunctionCall(toolCalls, outputIndex).arguments = arguments;
	}

	private StreamingToolCall ensureFunctionCall(final List<StreamingToolCall> toolCalls, final int outputIndex) {
		final var index = outputIndex < 0 ? toolCalls.size() : outputIndex;
		while (toolCalls.size() <= index) {
			toolCalls.add(new StreamingToolCall());
		}
		return toolCalls.get(index);
	}

	private List<ToolCall> extractToolCalls(final JsonNode output) {
		if (!output.isArray()) {
			return List.of();
		}
		final var toolCalls = new ArrayList<ToolCall>();
		for (final var item : output) {
			if (!"function_call".equals(this.text(item, "type"))) {
				continue;
			}
			final var callId = this.text(item, "call_id");
			toolCalls.add(new ToolCall(
					callId.isBlank() ? this.text(item, "id") : callId,
					"function",
					this.text(item, "name"),
					this.text(item, "arguments")));
		}
		return List.copyOf(toolCalls);
	}

	private List<ToolCall> toToolCalls(final List<StreamingToolCall> streamingToolCalls) {
		return streamingToolCalls.stream()
				.filter(toolCall -> toolCall.callId != null && !toolCall.callId.isBlank())
				.map(toolCall -> new ToolCall(toolCall.callId, toolCall.type == null ? "function" : toolCall.type, toolCall.name, toolCall.arguments))
				.toList();
	}

	private List<Map<String, Object>> extractOutputItems(final JsonNode output) {
		if (!output.isArray()) {
			return List.of();
		}
		final var outputItems = new ArrayList<Map<String, Object>>();
		for (final var item : output) {
			if (!item.isObject()) {
				continue;
			}
			outputItems.add(this.objectMapper.convertValue(item, this.objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)));
		}
		return List.copyOf(outputItems);
	}

	private String extractAssistantMessageText(final JsonNode output) {
		if (!output.isArray()) {
			return "";
		}
		final var response = new StringBuilder();
		for (final var item : output) {
			if (!"message".equals(this.text(item, "type")) || !"assistant".equals(this.text(item, "role"))) {
				continue;
			}
			for (final var contentItem : item.path("content")) {
				final var type = this.text(contentItem, "type");
				if ("output_text".equals(type) || "text".equals(type)) {
					response.append(this.text(contentItem, "text"));
				}
			}
		}
		return response.toString();
	}

	private String extractReasoningSummaryText(final JsonNode output) {
		if (!output.isArray()) {
			return "";
		}
		final var reasoning = new StringBuilder();
		for (final var item : output) {
			if (!"reasoning".equals(this.text(item, "type"))) {
				continue;
			}
			var hasSummary = false;
			for (final var summaryItem : item.path("summary")) {
				final var text = this.text(summaryItem, "text");
				if (!text.isEmpty()) {
					reasoning.append(text);
					hasSummary = true;
				}
			}
			if (!hasSummary) {
				for (final var contentItem : item.path("content")) {
					final var type = this.text(contentItem, "type");
					if ("reasoning_text".equals(type) || "text".equals(type)) {
						reasoning.append(this.text(contentItem, "text"));
					}
				}
			}
		}
		return reasoning.toString();
	}

	private String extractReasoningDelta(final String eventType, final JsonNode event) {
		if (eventType == null || !eventType.contains("reasoning")) {
			return "";
		}
		final var delta = this.text(event, "delta");
		if (!delta.isEmpty()) {
			return delta;
		}
		return this.text(event, "text");
	}

	private CompletionFinishReason determineFinishReason(final JsonNode response, final List<ToolCall> toolCalls) {
		if (!toolCalls.isEmpty()) {
			return CompletionFinishReason.TOOL_CALLS;
		}
		final var status = this.text(response, "status");
		return switch (status) {
			case "completed" -> CompletionFinishReason.DONE;
			case "failed" -> CompletionFinishReason.ERROR;
			case "incomplete" -> {
				final var incompleteReason = this.text(response.path("incomplete_details"), "reason");
				yield switch (incompleteReason) {
					case "max_output_tokens", "max_tokens", "length" -> CompletionFinishReason.TOKEN_LIMIT_REACHED;
					case "content_filter", "content_filtered" -> CompletionFinishReason.CONTENT_FILTERED;
					case "" -> CompletionFinishReason.DONE;
					default -> CompletionFinishReason.ERROR;
				};
			}
			default -> CompletionFinishReason.DONE;
		};
	}
	
	private CompletionStatistics createCompletionStatistics(final ModelProfile modelProfile, final LocalDateTime start, final JsonNode usage, final String prompt, final String response, final String reasoning) {
		var inTokens = 0;
		var cachedInTokens = 0;
		var outTokens = 0;
		var reasoningOutTokens = 0;

		if (usage != null && !usage.isMissingNode()) {
			final var inputTokens = this.integerValue(usage.path("input_tokens"), 0);
			cachedInTokens = this.integerValue(usage.path("input_tokens_details").path("cached_tokens"), 0);
			final var outputTokens = this.integerValue(usage.path("output_tokens"), 0);
			reasoningOutTokens = this.integerValue(usage.path("output_tokens_details").path("reasoning_tokens"), 0);
			inTokens = Math.max(0, inputTokens - cachedInTokens);
			outTokens = Math.max(0, outputTokens - reasoningOutTokens);
		}
		if (inTokens <= 0 && prompt != null && !prompt.isBlank()) {
			inTokens = this.countTokens(prompt, modelProfile);
		}
		if (reasoningOutTokens <= 0 && reasoning != null && !reasoning.isBlank()) {
			reasoningOutTokens = this.countTokens(reasoning, modelProfile);
			outTokens = Math.max(0, outTokens - reasoningOutTokens);
		}
		if (outTokens <= 0 && response != null && !response.isBlank()) {
			outTokens = this.countTokens(response, modelProfile);
		}

		final var cost = this.getUsageCost(usage)
				.max(this.calculateTokenCost(modelProfile, inTokens, cachedInTokens, outTokens, reasoningOutTokens));
		return new CompletionStatistics(
				1,
				Duration.between(start, LocalDateTime.now()),
				inTokens,
				cachedInTokens,
				outTokens,
				reasoningOutTokens,
				cost,
				false);
	}

	private BigDecimal getUsageCost(final JsonNode usage) {
		if (usage == null || usage.isMissingNode()) {
			return BigDecimal.ZERO;
		}
		var cost = this.decimalValue(usage.path("cost"));
		if (cost.compareTo(BigDecimal.ZERO) > 0) {
			return cost;
		}
		cost = this.decimalValue(usage.path("cost_details").path("upstream_inference_cost"));
		if (cost.compareTo(BigDecimal.ZERO) > 0) {
			return cost;
		}
		cost = cost.add(this.decimalValue(usage.path("cost_details").path("upstream_inference_prompt_cost")));
		cost = cost.add(this.decimalValue(usage.path("cost_details").path("upstream_inference_completions_cost")));
		cost = cost.add(this.decimalValue(usage.path("cost_details").path("upstream_inference_input_cost")));
		cost = cost.add(this.decimalValue(usage.path("cost_details").path("upstream_inference_output_cost")));
		return cost;
	}

	private BigDecimal calculateTokenCost(final ModelProfile modelProfile, final int inTokens, final int cachedInTokens, final int outTokens, final int reasoningOutTokens) {
		var cost = BigDecimal.ZERO;
		cost = cost.add(new BigDecimal(inTokens).multiply(modelProfile.costPerInToken()));
		cost = cost.add(new BigDecimal(cachedInTokens).multiply(modelProfile.costPerCachedInToken()));
		cost = cost.add(new BigDecimal(outTokens).multiply(modelProfile.costPerOutToken()));
		cost = cost.add(new BigDecimal(reasoningOutTokens).multiply(modelProfile.costPerReasoningOutToken()));
		return cost;
	}

	private List<String> loadModelNames(final ModelProfile modelProfile) {
		try {
			var requestSpec = this.restClientBuilder.baseUrl(modelProfile.url()).build()
					.method(HttpMethod.GET)
					.uri("/v1/models");
			if (modelProfile.apiKey() != null && !modelProfile.apiKey().isBlank()) {
				requestSpec = requestSpec.header("Authorization", "Bearer " + modelProfile.apiKey());
			}
			final var response = requestSpec.retrieve().body(String.class);
			if (response == null || response.isBlank()) {
				return List.of();
			}
			final var root = this.objectMapper.readTree(response);
			final var models = new ArrayList<String>();
			for (final var item : root.path("data")) {
				final var modelName = this.text(item, "id");
				if (!modelName.isBlank()) {
					models.add(modelName);
				}
			}
			return List.copyOf(models);
		}
		catch (final RestClientResponseException e) {
			LOGGER.warn("Failed to load model names: {} {}", e.getStatusCode(), e.getStatusText());
			return List.of();
		}
		catch (final RestClientException e) {
			LOGGER.warn("Failed to load model names: {}", e.getMessage());
			return List.of();
		}
		catch (final RuntimeException e) {
			LOGGER.warn("Failed to load model names", e);
			return List.of();
		}
	}

	private String getPrimaryModelName(final ModelProfile modelProfile) {
		if (modelProfile.modelName() != null && !modelProfile.modelName().isBlank()) {
			return modelProfile.modelName();
		}
		return this.getModelNames(modelProfile, false).stream().findFirst().orElse("");
	}

	private String getErrorMessage(final String errorBody) {
		if (errorBody == null || errorBody.isBlank()) {
			return "";
		}
		try {
			final var errorNode = this.objectMapper.readTree(errorBody).path("error").path("message");
			final var error = this.nodeText(errorNode);
			return error.isBlank() ? errorBody : error;
		}
		catch (final JacksonException e) {
			LOGGER.debug("Failed to parse error response body", e);
			return errorBody;
		}
	}

	private String getEventErrorMessage(final JsonNode event) {
		final var message = this.text(event.path("error"), "message");
		if (!message.isEmpty()) {
			return message;
		}
		final var directMessage = this.text(event, "message");
		if (!directMessage.isEmpty()) {
			return directMessage;
		}
		return event.toString();
	}

	private OpenAiResponsesApiDialect responsesDialect() {
		return this.openAiResponsesApiDialect == null ? DEFAULT_DIALECT : this.openAiResponsesApiDialect;
	}

	private Map<String, Object> createReasoningRequest(final OpenAiResponsesApiDialect.ResponsesReasoning reasoning) {
		if (reasoning == null) {
			return null;
		}
		final var request = new LinkedHashMap<String, Object>();
		if (reasoning.effort() != null) {
			request.put("effort", reasoning.effort());
		}
		if (reasoning.generateSummary() != null) {
			request.put("generate_summary", reasoning.generateSummary());
		}
		if (reasoning.summary() != null) {
			request.put("summary", reasoning.summary());
		}
		return request.isEmpty() ? null : request;
	}

	private List<String> createIncludeList(final ModelProfile modelProfile, final boolean store) {
		final var include = new LinkedHashSet<>(this.responsesDialect().include(modelProfile));
		if (!store) {
			include.add(OpenAiResponsesApiDialect.INCLUDE_REASONING_ENCRYPTED_CONTENT);
		}
		return List.copyOf(include);
	}

	private Map<String, Object> createStreamOptionsRequest(final OpenAiResponsesApiDialect.ResponsesStreamOptions streamOptions) {
		if (streamOptions == null) {
			return null;
		}
		final var request = new LinkedHashMap<String, Object>();
		if (streamOptions.includeObfuscation() != null) {
			request.put("include_obfuscation", streamOptions.includeObfuscation());
		}
		return request.isEmpty() ? null : request;
	}

	private Map<String, Object> createTextRequest(final OpenAiResponsesApiDialect.ResponsesText text) {
		if (text == null) {
			return null;
		}
		final var request = new LinkedHashMap<String, Object>();
		if (text.verbosity() != null) {
			request.put("verbosity", text.verbosity());
		}
		return request.isEmpty() ? null : request;
	}

	private List<Map<String, Object>> createFallbackOutputItems(final String assistantText, final List<ToolCall> toolCalls) {
		final var outputItems = new ArrayList<Map<String, Object>>();
		if (assistantText != null && !assistantText.isBlank()) {
			final var messageItem = new LinkedHashMap<String, Object>();
			messageItem.put("type", "message");
			messageItem.put("role", "assistant");
			messageItem.put("phase", this.resolveAssistantPhase(null, toolCalls));
			messageItem.put("status", "completed");
			messageItem.put("content", List.of(Map.of("type", "output_text", "text", assistantText)));
			outputItems.add(messageItem);
		}
		for (final var toolCall : toolCalls) {
			final var functionCallItem = new LinkedHashMap<String, Object>();
			functionCallItem.put("type", "function_call");
			functionCallItem.put("call_id", toolCall.id());
			functionCallItem.put("name", toolCall.name());
			functionCallItem.put("arguments", toolCall.arguments());
			functionCallItem.put("status", "completed");
			outputItems.add(functionCallItem);
		}
		return List.copyOf(outputItems);
	}

	private void appendInputText(final StringBuilder inputTextBuilder, final String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		if (!inputTextBuilder.isEmpty()) {
			inputTextBuilder.append('\n');
		}
		inputTextBuilder.append(text);
	}

	private void appendInputItemText(final StringBuilder inputTextBuilder, final Map<String, Object> inputItem) {
		if (inputItem == null || inputItem.isEmpty()) {
			return;
		}
		try {
			this.appendInputText(inputTextBuilder, this.objectMapper.writeValueAsString(inputItem));
		}
		catch (final JacksonException e) {
			this.appendInputText(inputTextBuilder, inputItem.toString());
		}
	}

	private String resolveAssistantPhase(final String phase, final List<ToolCall> toolCalls) {
		if (phase != null && !phase.isBlank()) {
			return phase;
		}
		return toolCalls != null && !toolCalls.isEmpty() ? ASSISTANT_PHASE_COMMENTARY : ASSISTANT_PHASE_FINAL_ANSWER;
	}

	private Map<String, Object> normalizeAssistantOutputItemPhase(final Map<String, Object> outputItem, final boolean containsFunctionCalls) {
		if (outputItem == null || outputItem.isEmpty()) {
			return Map.of();
		}
		if (!"message".equals(outputItem.get("type")) || !"assistant".equals(outputItem.get("role")) || outputItem.get("phase") instanceof final String phase && !phase.isBlank()) {
			return outputItem;
		}
		final var normalizedOutputItem = new LinkedHashMap<>(outputItem);
		normalizedOutputItem.put("phase", containsFunctionCalls ? ASSISTANT_PHASE_COMMENTARY : ASSISTANT_PHASE_FINAL_ANSWER);
		return Map.copyOf(normalizedOutputItem);
	}

	private int countTokens(final String text, final ModelProfile modelProfile) {
		return text == null || text.isBlank() ? 0 : this.tokenizerResolver.count(text, modelProfile);
	}

	private String text(final JsonNode node, final String field) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return "";
		}
		return this.nodeText(node.path(field));
	}

	private int integerValue(final JsonNode node, final int defaultValue) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return defaultValue;
		}
		return node.canConvertToInt() ? node.intValue() : defaultValue;
	}

	private BigDecimal decimalValue(final JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return BigDecimal.ZERO;
		}
		try {
			return new BigDecimal(this.nodeText(node));
		}
		catch (final NumberFormatException e) {
			return BigDecimal.ZERO;
		}
	}

	private String nodeText(final JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return "";
		}
		switch (node.getNodeType()) {
			case STRING:
				try {
					return this.objectMapper.treeToValue(node, String.class);
				}
				catch (final JacksonException e) {
					return "";
				}
			case NUMBER, BOOLEAN:
				return node.toString();
			default:
				return "";
		}
	}

	private static final class StreamingToolCall {

		private String callId;

		private String type;

		private String name;

		private String arguments;
	}

}
