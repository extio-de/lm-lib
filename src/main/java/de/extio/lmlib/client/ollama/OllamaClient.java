package de.extio.lmlib.client.ollama;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.CompletionStatistics;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.ToolCall;
import de.extio.lmlib.client.ToolCallData;
import de.extio.lmlib.client.ToolDefinition;
import de.extio.lmlib.client.oai.completion.FinishReasons;
import de.extio.lmlib.client.ollama.OllamaDialect.ThinkMode;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;
import de.extio.lmlib.profile.ModelProfileService;
import de.extio.lmlib.token.TokenizerResolver;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class OllamaClient implements Client {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(OllamaClient.class);

	private static final OllamaDialect DEFAULT_OLLAMA_DIALECT = new OllamaDialect() {
	};
	
	private final Map<String, List<String>> resolvedModelNames = new ConcurrentHashMap<>();

	private final Map<String, OllamaShowResponse> resolvedModelDetails = new ConcurrentHashMap<>();
	
	private final ObjectMapper objectMapper = JsonMapper.builder()
			.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
			.changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.build();
	
	@Autowired
	@Qualifier("lmLibRestClientBuilder")
	private RestClient.Builder restClientBuilder;
	
	@Autowired
	private TokenizerResolver tokenizerResolver;
	
	@Autowired
	private ModelProfileService modelProfileService;

	@Autowired(required = false)
	private OllamaDialect ollamaDialect;
	
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
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.OLLAMA) {
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
		
		LOGGER.debug("Requesting Ollama chat at {}", modelProfile.url());
		final var start = LocalDateTime.now();
		final var restClient = this.restClientBuilder.baseUrl(modelProfile.url()).build();
		
		var requestSpec = restClient
				.method(HttpMethod.POST)
				.uri("/api/chat")
				.header("Content-Type", "application/json");
		if (modelProfile.apiKey() != null && !modelProfile.apiKey().isBlank()) {
			requestSpec = requestSpec.header("Authorization", "Bearer " + modelProfile.apiKey());
		}
		
		final var response = requestSpec
				.body(requestBody)
				.exchange((clientRequest, clientResponse) -> {
					if (clientResponse.getStatusCode().isError()) {
						this.logErrorResponse(clientResponse.getStatusCode(), clientResponse.getBody());
						throw new IllegalStateException("Error response from server: " + clientResponse.getStatusCode() + " " + clientResponse.getBody());
					}
					if (chunkConsumer == null) {
						final var ollamaResponse = this.objectMapper.readValue(clientResponse.getBody(), OllamaChatResponse.class);
						this.throwIfEmbeddedError(ollamaResponse);
						return ollamaResponse;
					}
					return this.readStream(clientResponse.getBody(), chunkConsumer);
				});
		
		this.throwIfEmbeddedError(response);
		final var content = response.getMessage() != null ? response.getMessage().getContent() : null;
		final var reasoning = response.getMessage() != null ? response.getMessage().getThinking() : null;
		final var toolCalls = this.toToolCalls(response.getMessage() != null ? response.getMessage().getToolCalls() : List.of());
		final var finishReason = this.mapFinishReason(response.getDoneReason(), toolCalls);
		final var statistics = this.createCompletionStatistics(modelProfile, start, response, conversation, content, reasoning);
		return new Completion(content, reasoning, finishReason, statistics, toolCalls);
	}
	
	@Override
	public List<String> getModelNames(final ModelProfile modelProfile, final boolean forceReload) {
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.OLLAMA) {
			return List.of();
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
		return ModelProvider.OLLAMA;
	}
	
	@Override
	public boolean supportsToolCalling(final ModelCategory modelCategory) {
		final var modelProfile = this.modelProfileService.getModelProfile(modelCategory.getModelProfile(), modelCategory);
		return this.supportsToolCalling(modelProfile);
	}

	@Override
	public boolean supportsToolCalling(final ModelProfile modelProfile) {
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.OLLAMA) {
			return false;
		}
		final var modelName = this.getPrimaryModelName(modelProfile);
		if (modelName == null || modelName.isBlank()) {
			return false;
		}
		return this.supportsToolCapability(this.getModelDetails(modelProfile, modelName));
	}
	
	private OllamaChatRequest createRequest(final Conversation conversation, final ModelProfile modelProfile, final boolean streaming, final ToolCallData toolCallData) {
		final var modelName = this.getPrimaryModelName(modelProfile);
		if (modelName == null || modelName.isBlank()) {
			throw new IllegalStateException("No model name resolved for Ollama profile " + modelProfile.category());
		}
		final var modelDetails = this.getModelDetails(modelProfile, modelName);
		final var request = new OllamaChatRequest();
		request.setModel(modelName);
		request.setMessages(this.createMessages(conversation));
		request.setStream(streaming);
		final var think = this.createThinkRequestValue(modelProfile, modelDetails);
		if (think != null) {
			request.setThink(think.getWireValue());
		}
		request.setOptions(this.createOptions(modelProfile));
		if (toolCallData != null && toolCallData.hasTools()) {
			if (this.supportsToolCapability(modelDetails)) {
				request.setTools(this.createTools(toolCallData.tools()));
			}
			else {
				LOGGER.debug("Ollama model {} does not advertise tool capability. Tools will be omitted from the request.", modelName);
			}
		}
		return request;
	}

	private ThinkMode createThinkRequestValue(final ModelProfile modelProfile, final OllamaShowResponse modelDetails) {
		final var thinkMode = this.getOllamaDialect().think(modelProfile);
		if (thinkMode == null) {
			return null;
		}
		if (!this.supportsThinkingCapability(modelDetails)) {
			LOGGER.debug("Ollama model {} does not advertise thinking capability. Think will be omitted from the request.", this.getPrimaryModelName(modelProfile));
			return null;
		}
		return thinkMode;
	}
	
	private List<OllamaMessage> createMessages(final Conversation conversation) {
		final List<OllamaMessage> messages = new ArrayList<>();
		final Map<String, String> toolCallNamesById = new LinkedHashMap<>();
		for (final Turn turn : conversation.getConversation()) {
			final var message = new OllamaMessage();
			message.setRole(this.mapRole(turn));
			message.setContent(turn.text() != null ? turn.text() : "");
			if (!turn.toolCalls().isEmpty()) {
				message.setToolCalls(turn.toolCalls().stream().map(toolCall -> {
					final var function = new OllamaFunctionCall();
					function.setName(toolCall.name());
					function.setArguments(this.parseJsonOrText(toolCall.arguments()));
					final var ollamaToolCall = new OllamaToolCall();
					ollamaToolCall.setId(toolCall.id());
					ollamaToolCall.setType(toolCall.type());
					ollamaToolCall.setFunction(function);
					if (toolCall.id() != null && !toolCall.id().isBlank() && toolCall.name() != null && !toolCall.name().isBlank()) {
						toolCallNamesById.put(toolCall.id(), toolCall.name());
					}
					return ollamaToolCall;
				}).toList());
			}
			message.setToolCallId(turn.toolCallId());
			if (turn.type() == Conversation.TurnType.TOOL && turn.toolCallId() != null && !turn.toolCallId().isBlank()) {
				message.setToolName(toolCallNamesById.get(turn.toolCallId()));
			}
			messages.add(message);
		}
		return messages;
	}
	
	private String mapRole(final Turn turn) {
		return switch (turn.type()) {
			case SYSTEM -> "system";
			case USER -> "user";
			case ASSISTANT -> "assistant";
			case TOOL -> "tool";
		};
	}
	
	private Map<String, Object> createOptions(final ModelProfile modelProfile) {
		final var options = new LinkedHashMap<String, Object>();
		options.put("num_predict", modelProfile.maxTokens());
		options.put("temperature", modelProfile.temperature());
		options.put("top_p", modelProfile.topP());
		return options;
	}
	
	private List<OllamaToolDefinition> createTools(final List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			final var function = new OllamaToolFunction();
			function.setName(toolDefinition.name());
			function.setDescription(toolDefinition.description());
			function.setParameters(toolDefinition.parameters());
			final var tool = new OllamaToolDefinition();
			tool.setType("function");
			tool.setFunction(function);
			return tool;
		}).toList();
	}
	
	private OllamaChatResponse readStream(final InputStream inputStream, final Consumer<Chunk> chunkConsumer) throws IOException {
		final var response = new OllamaChatResponse();
		final var contentBuilder = new StringBuilder();
		final var reasoningBuilder = new StringBuilder();
		final List<ToolCall> toolCalls = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				final var streamResponse = this.objectMapper.readValue(line, OllamaChatResponse.class);
				this.throwIfEmbeddedError(streamResponse);
				response.copyFrom(streamResponse);
				if (streamResponse.getMessage() != null) {
					String content = null;
					String reasoning = null;
					if (streamResponse.getMessage().getContent() != null && !streamResponse.getMessage().getContent().isEmpty()) {
						content = streamResponse.getMessage().getContent();
						contentBuilder.append(content);
					}
					if (streamResponse.getMessage().getThinking() != null && !streamResponse.getMessage().getThinking().isEmpty()) {
						reasoning = streamResponse.getMessage().getThinking();
						reasoningBuilder.append(reasoning);
					}
					if (content != null || reasoning != null) {
						chunkConsumer.accept(new Chunk(content, reasoning));
					}
					if (streamResponse.getMessage().getToolCalls() != null && !streamResponse.getMessage().getToolCalls().isEmpty()) {
						toolCalls.clear();
						toolCalls.addAll(this.toToolCalls(streamResponse.getMessage().getToolCalls()));
					}
				}
				if (Boolean.TRUE.equals(streamResponse.getDone())) {
					break;
				}
			}
		}
		final var message = new OllamaMessage();
		message.setRole("assistant");
		message.setContent(contentBuilder.toString());
		message.setThinking(reasoningBuilder.toString());
		message.setToolCalls(this.toOllamaToolCalls(toolCalls));
		response.setMessage(message);
		return response;
	}
	
	private List<ToolCall> toToolCalls(final List<OllamaToolCall> ollamaToolCalls) {
		if (ollamaToolCalls == null || ollamaToolCalls.isEmpty()) {
			return List.of();
		}
		final List<ToolCall> toolCalls = new ArrayList<>();
		for (int i = 0; i < ollamaToolCalls.size(); i++) {
			final var toolCall = ollamaToolCalls.get(i);
			if (toolCall == null || toolCall.getFunction() == null || toolCall.getFunction().getName() == null || toolCall.getFunction().getName().isBlank()) {
				continue;
			}
			toolCalls.add(new ToolCall(
					toolCall.getId() != null && !toolCall.getId().isBlank() ? toolCall.getId() : "ollama-tool-call-" + (i + 1),
					toolCall.getType() != null && !toolCall.getType().isBlank() ? toolCall.getType() : "function",
					toolCall.getFunction().getName(),
					this.serializeArguments(toolCall.getFunction().getArguments())));
		}
		return List.copyOf(toolCalls);
	}
	
	private List<OllamaToolCall> toOllamaToolCalls(final List<ToolCall> toolCalls) {
		return toolCalls.stream().map(toolCall -> {
			final var function = new OllamaFunctionCall();
			function.setName(toolCall.name());
			function.setArguments(this.parseJsonOrText(toolCall.arguments()));
			final var ollamaToolCall = new OllamaToolCall();
			ollamaToolCall.setId(toolCall.id());
			ollamaToolCall.setType(toolCall.type());
			ollamaToolCall.setFunction(function);
			return ollamaToolCall;
		}).toList();
	}
	
	private CompletionFinishReason mapFinishReason(final String finishReason, final List<ToolCall> toolCalls) {
		if (toolCalls != null && !toolCalls.isEmpty()) {
			return CompletionFinishReason.TOOL_CALLS;
		}
		return switch (finishReason) {
			case FinishReasons.FINISH_REASON_LENGTH -> CompletionFinishReason.TOKEN_LIMIT_REACHED;
			case FinishReasons.FINISH_REASON_CONTENT_FILTER -> CompletionFinishReason.CONTENT_FILTERED;
			case FinishReasons.FINISH_REASON_TOOL_CALLS -> CompletionFinishReason.TOOL_CALLS;
			case FinishReasons.FINISH_REASON_ERROR -> CompletionFinishReason.ERROR;
			default -> CompletionFinishReason.DONE;
		};
	}
	
	private CompletionStatistics createCompletionStatistics(final ModelProfile modelProfile, final LocalDateTime start, final OllamaChatResponse response, final Conversation conversation, final String content, final String reasoning) {
		int inTokens = 0;
		int outTokens = 0;
		int reasoningOutTokens = 0;
		if (response.getPromptEvalCount() != null && response.getPromptEvalCount() > 0) {
			inTokens = response.getPromptEvalCount();
		}
		if (response.getEvalCount() != null && response.getEvalCount() > 0) {
			outTokens = response.getEvalCount();
		}
		if (reasoning != null && !reasoning.isBlank()) {
			reasoningOutTokens = this.tokenizerResolver.count(reasoning, modelProfile);
			outTokens = Math.max(0, outTokens - reasoningOutTokens);
		}
		if (inTokens <= 0) {
			inTokens = this.tokenizerResolver.count(this.createPromptForCounting(conversation), modelProfile);
		}
		if (outTokens <= 0 && content != null && !content.isBlank()) {
			outTokens = this.tokenizerResolver.count(content, modelProfile);
		}
		final var duration = response.getTotalDuration() != null && response.getTotalDuration() > 0
				? Duration.ofNanos(response.getTotalDuration())
				: Duration.between(start, LocalDateTime.now());
		var cost = BigDecimal.ZERO;
		cost = cost.add(BigDecimal.valueOf(inTokens).multiply(modelProfile.costPerInToken()));
		cost = cost.add(BigDecimal.valueOf(outTokens).multiply(modelProfile.costPerOutToken()));
		cost = cost.add(BigDecimal.valueOf(reasoningOutTokens).multiply(modelProfile.costPerReasoningOutToken()));
		return new CompletionStatistics(1, duration, inTokens, 0, outTokens, reasoningOutTokens, cost, false);
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
	
	private List<String> loadModelNames(final ModelProfile modelProfile) {
		try {
			var requestSpec = this.restClientBuilder.baseUrl(modelProfile.url()).build()
					.method(HttpMethod.GET)
					.uri("/api/tags");
			if (modelProfile.apiKey() != null && !modelProfile.apiKey().isBlank()) {
				requestSpec = requestSpec.header("Authorization", "Bearer " + modelProfile.apiKey());
			}
			final var response = requestSpec.retrieve().body(OllamaTagsResponse.class);
			return response == null || response.getModels() == null ? List.of()
					: response.getModels().stream()
							.map(model -> model.getName() != null && !model.getName().isBlank() ? model.getName() : model.getModel())
							.filter(Objects::nonNull)
							.filter(modelName -> !modelName.isBlank())
							.toList();
		}
		catch (final RestClientResponseException e) {
			LOGGER.error("Failed to load Ollama model names: {} {}", e.getStatusCode(), e.getStatusText());
			throw new IllegalStateException("Failed to load Ollama model names: " + e.getStatusCode() + " " + e.getStatusText(), e);
		}
		catch (final RestClientException e) {
			LOGGER.error("Failed to load Ollama model names: {}", e.getMessage());
			throw new IllegalStateException("Failed to load Ollama model names: " + e.getMessage(), e);
		}
		catch (final RuntimeException e) {
			LOGGER.error("Failed to load Ollama model names", e);
			throw new IllegalStateException("Failed to load Ollama model names: " + e.getMessage(), e);
		}
	}

	private OllamaShowResponse getModelDetails(final ModelProfile modelProfile, final String modelName) {
		final var cacheKey = this.cacheKey(modelProfile, modelName);
		return this.resolvedModelDetails.computeIfAbsent(cacheKey, key -> this.loadModelDetails(modelProfile, modelName));
	}

	private OllamaShowResponse loadModelDetails(final ModelProfile modelProfile, final String modelName) {
		try {
			final var request = new OllamaShowRequest();
			request.setModel(modelName);
			request.setVerbose(false);
			var requestSpec = this.restClientBuilder.baseUrl(modelProfile.url()).build()
					.method(HttpMethod.POST)
					.uri("/api/show")
					.header("Content-Type", "application/json");
			if (modelProfile.apiKey() != null && !modelProfile.apiKey().isBlank()) {
				requestSpec = requestSpec.header("Authorization", "Bearer " + modelProfile.apiKey());
			}
			final var requestBody = this.objectMapper.writeValueAsString(request);
			final var response = requestSpec.body(requestBody).retrieve().body(OllamaShowResponse.class);
			return response == null ? new OllamaShowResponse() : response;
		}
		catch (final RestClientResponseException e) {
			LOGGER.warn("Failed to load Ollama model details for {}: {} {}", modelName, e.getStatusCode(), e.getStatusText());
			return new OllamaShowResponse();
		}
		catch (final RestClientException e) {
			LOGGER.warn("Failed to load Ollama model details for {}: {}", modelName, e.getMessage());
			return new OllamaShowResponse();
		}
		catch (final JacksonException e) {
			LOGGER.warn("Failed to serialize Ollama show request for {}", modelName, e);
			return new OllamaShowResponse();
		}
		catch (final RuntimeException e) {
			LOGGER.warn("Failed to load Ollama model details for {}", modelName, e);
			return new OllamaShowResponse();
		}
	}

	private boolean supportsThinkingCapability(final OllamaShowResponse modelDetails) {
		return this.hasCapability(modelDetails, "thinking");
	}

	private boolean supportsToolCapability(final OllamaShowResponse modelDetails) {
		return this.hasCapability(modelDetails, "tools")
				|| this.hasCapability(modelDetails, "tool")
				|| this.hasCapability(modelDetails, "tool-calling")
				|| this.hasCapability(modelDetails, "tool_calling");
	}

	private boolean hasCapability(final OllamaShowResponse modelDetails, final String expectedCapability) {
		if (modelDetails == null || modelDetails.getCapabilities() == null || modelDetails.getCapabilities().isEmpty()) {
			return false;
		}
		return modelDetails.getCapabilities().stream()
				.filter(Objects::nonNull)
				.map(capability -> capability.toLowerCase(Locale.ROOT).trim())
				.anyMatch(capability -> capability.equals(expectedCapability) || capability.contains(expectedCapability));
	}

	private String cacheKey(final ModelProfile modelProfile, final String modelName) {
		return modelProfile.category() + "|" + modelProfile.url() + "|" + modelProfile.apiKey() + "|" + modelName;
	}

	private OllamaDialect getOllamaDialect() {
		return this.ollamaDialect == null ? DEFAULT_OLLAMA_DIALECT : this.ollamaDialect;
	}
	
	private void throwIfEmbeddedError(final OllamaChatResponse response) {
		if (response != null && response.getError() != null && !response.getError().isBlank()) {
			LOGGER.warn("Error response from Ollama: {}", response.getError());
			throw new IllegalStateException(response.getError());
		}
	}
	
	private void logErrorResponse(final HttpStatusCode statusCode, final InputStream responseBody) {
		try {
			LOGGER.warn("Error response from server: {} {}", statusCode, new String(responseBody.readAllBytes(), StandardCharsets.UTF_8));
		}
		catch (final IOException e) {
			LOGGER.warn("Error response from server: {} <failed to read response body: {}>", statusCode, e.getMessage());
		}
	}
	
	private Object parseJsonOrText(final String value) {
		if (value == null || value.isBlank()) {
			return value;
		}
		try {
			return this.objectMapper.readValue(value, Object.class);
		}
		catch (final Exception e) {
			return value;
		}
	}
	
	private String serializeArguments(final Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof final String string) {
			return string;
		}
		try {
			return this.objectMapper.writeValueAsString(value);
		}
		catch (final JacksonException e) {
			throw new IllegalStateException("Cannot serialize tool call arguments", e);
		}
	}
	
}
