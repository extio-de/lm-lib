package de.extio.lmlib.client.oai.completion.chat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import tools.jackson.core.JacksonException;

import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.ToolCall;
import de.extio.lmlib.client.ToolCallData;
import de.extio.lmlib.client.ToolDefinition;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.oai.completion.AbstractCompletionClient;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;

public class ChatCompletionClient extends AbstractCompletionClient {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ChatCompletionClient.class);
	
	public ChatCompletionClient() {
		super();
	}
	
	@Override
	public boolean supportsToolCalling() {
		return true;
	}

	@Override
	public ModelProvider getModelProvider() {
		return ModelProvider.OAI_CHAT_COMPLETION;
	}
	
	@Override
	protected Completion requestCompletion(final Conversation conversation, final ModelProfile modelProfile, final Consumer<Chunk> chunkConsumer, final ToolCallData toolCallData) {
		final var chat = createChats(conversation, modelProfile);
		
		final var request = new ChatCompletionRequest();
		request.setModel(this.getPrimaryModelName(modelProfile));
		request.setMessages(chat);
		request.setMaxTokens(modelProfile.maxTokens());
		request.setMaxCompletionTokens(modelProfile.maxTokens());
		request.setTemperature(modelProfile.temperature());
		request.setTopP(modelProfile.topP());
		request.setStream(chunkConsumer != null);
		if (chunkConsumer != null) {
			this.configureStreamOptions(true, request::setStreamOptions);
		} else {
			request.setUsage(true);
		}
		if (modelProfile.reasoningEffort() != null && !modelProfile.reasoningEffort().isBlank()) {
			request.setReasoning(new ChatCompletionRequest.ChatCompletionsRequestReasoning());
			try {
				request.getReasoning().setEffort(ChatCompletionRequest.ChatCompletionsRequestReasoningEffort.valueOf(modelProfile.reasoningEffort()));
			}
			catch (final IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid reasoning effort in the model profile: " + modelProfile.reasoningEffort(), e);
			}
		}
		if (modelProfile.reasoningSummaryDetails() != null && !modelProfile.reasoningSummaryDetails().isBlank()) {
			if (request.getReasoning() == null) {
				request.setReasoning(new ChatCompletionRequest.ChatCompletionsRequestReasoning());
			}
			try {
				request.getReasoning().setSummary(ChatCompletionRequest.ChatCompletionsRequestReasoningSummaryDetails.valueOf(modelProfile.reasoningSummaryDetails()).name());
			}
			catch (final IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid reasoning summary details in the model profile: " + modelProfile.reasoningSummaryDetails(), e);
			}
		}
		if (toolCallData != null && toolCallData.hasTools()) {
			request.setTools(this.createTools(toolCallData.tools()));
			request.setToolChoice(this.createToolChoice(toolCallData));
			request.setParallelToolCalls(toolCallData.parallelToolCalls());
		}
		
		String requestBody;
		try {
			requestBody = this.objectMapper.writeValueAsString(request);
		}
		catch (final JacksonException e) {
			throw new IllegalStateException("Cannot serialize request body", e);
		}
		
		LOGGER.debug("Requesting chat completion at {}", modelProfile.url());
		final LocalDateTime start = LocalDateTime.now();
		final var restClient = this.restClientBuilder.baseUrl(modelProfile.url()).build();
		
		var requestSpec = restClient
				.method(HttpMethod.POST)
				.uri("/v1/chat/completions")
				.header("Content-Type", "application/json");
		if (modelProfile.apiKey() != null && !modelProfile.apiKey().isBlank()) {
			requestSpec = requestSpec.header("Authorization", "Bearer " + modelProfile.apiKey());
		}
		
		final var response = requestSpec
				.body(requestBody)
				.exchange((clientRequest, clientResponse) -> {
					if (clientResponse.getStatusCode().isError()) {
						throw new IllegalStateException("Error response from server: " + clientResponse.getStatusCode());
					}
					
					if (chunkConsumer == null) {
						return this.objectMapper.readValue(clientResponse.getBody(), ChatCompletionResponse.class);
					}
					else {
						final var chatCompletionResponse = new ChatCompletionResponse();
						chatCompletionResponse.setChoices(List.of(new ChatChoice()));
						final StringBuilder contentStringBuilder = new StringBuilder();
						final StringBuilder reasoningStringBuilder = new StringBuilder();
						final List<ChatToolCall> toolCalls = new ArrayList<>();
						
						try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientResponse.getBody(), StandardCharsets.UTF_8))) {
							String line;
							while ((line = reader.readLine()) != null) {
								if (line.startsWith("data: ")) {
									line = line.substring(6).trim();
								}
								if (line.isEmpty()) {
									continue;
								}
								if ("[DONE]".equals(line)) {
									break;
								}
								
								try {
									final var streamResponse = this.objectMapper.readValue(line, ChatCompletionResponse.class);
									
									if (streamResponse.getId() != null && !streamResponse.getId().isEmpty()) {
										chatCompletionResponse.setId(streamResponse.getId());
									}
									if (streamResponse.getObject() != null && !streamResponse.getObject().isEmpty()) {
										chatCompletionResponse.setObject(streamResponse.getObject());
									}
									if (streamResponse.getCreated() != null) {
										chatCompletionResponse.setCreated(streamResponse.getCreated());
									}
									if (streamResponse.getUsage() != null) {
										chatCompletionResponse.setUsage(streamResponse.getUsage());
									}
									if (streamResponse.getTimings() != null) {
										chatCompletionResponse.setTimings(streamResponse.getTimings());
									}
									if (streamResponse.getChoices() != null && !streamResponse.getChoices().isEmpty()) {
										if (streamResponse.getChoices().getFirst().getFinishReason() != null) {
											chatCompletionResponse.getChoices().getFirst().setFinishReason(streamResponse.getChoices().getFirst().getFinishReason());
										}
										if (streamResponse.getChoices().getFirst().getMessage() != null) {
											String content = null;
											String reasoning = null;
											if (streamResponse.getChoices().getFirst().getMessage().getContent() != null && !streamResponse.getChoices().getFirst().getMessage().getContent().isEmpty()) {
												content = streamResponse.getChoices().getFirst().getMessage().getContent();
												contentStringBuilder.append(content);
											}
											if (streamResponse.getChoices().getFirst().getMessage().getReasoningContent() != null && !streamResponse.getChoices().getFirst().getMessage().getReasoningContent().isEmpty()) {
												reasoning = streamResponse.getChoices().getFirst().getMessage().getReasoningContent();
												reasoningStringBuilder.append(reasoning);
											}
											if (content != null || reasoning != null) {
												chunkConsumer.accept(new Chunk(content, reasoning));
											}
											if (streamResponse.getChoices().getFirst().getMessage().getToolCalls() != null && !streamResponse.getChoices().getFirst().getMessage().getToolCalls().isEmpty()) {
												this.mergeToolCalls(toolCalls, streamResponse.getChoices().getFirst().getMessage().getToolCalls());
											}
										}
									}
								}
								catch (final JacksonException e) {
									LOGGER.warn("Streamed chunk is not parseable: {}", line, e);
								}
							}
						}
						chatCompletionResponse.getChoices().getFirst().setMessage(new ChatMessage("assistant", contentStringBuilder.toString(), reasoningStringBuilder.toString()));
						chatCompletionResponse.getChoices().getFirst().getMessage().setToolCalls(List.copyOf(toolCalls));
						return chatCompletionResponse;
					}
				});
		
		if (response.getChoices() == null || response.getChoices().isEmpty() || response.getChoices().getFirst().getMessage() == null) {
			throw new IllegalStateException("No parseable response content from server");
		}
		final var choice = response.getChoices().getFirst();
		final var content = response.getChoices().getFirst().getMessage().getContent();
		final var reasoning = response.getChoices().getFirst().getMessage().getReasoningContent();
		final var toolCalls = this.toToolCalls(response.getChoices().getFirst().getMessage().getToolCalls());
		final var finishReason = this.mapFinishReason(choice.getFinishReason());
		final var statistics = createCompletionStatistics(modelProfile, start, response.getUsage(), response.getTimings(), null, content, reasoning);
		return new Completion(content, reasoning, finishReason, statistics, toolCalls);
	}

	private List<ChatCompletionRequest.ChatCompletionTool> createTools(final List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			final var function = new ChatCompletionRequest.ChatCompletionFunction();
			function.setName(toolDefinition.name());
			function.setDescription(toolDefinition.description());
			function.setParameters(toolDefinition.parameters());
			function.setStrict(toolDefinition.strict());

			final var tool = new ChatCompletionRequest.ChatCompletionTool();
			tool.setType("function");
			tool.setFunction(function);
			return tool;
		}).toList();
	}

	private Object createToolChoice(final ToolCallData toolCallData) {
		if (toolCallData == null) {
			return null;
		}
		if (toolCallData.forcedToolName() != null && !toolCallData.forcedToolName().isBlank()) {
			final var function = new ChatCompletionRequest.ChatCompletionNamedToolChoiceFunction();
			function.setName(toolCallData.forcedToolName());

			final var namedToolChoice = new ChatCompletionRequest.ChatCompletionNamedToolChoice();
			namedToolChoice.setType("function");
			namedToolChoice.setFunction(function);
			return namedToolChoice;
		}
		return toolCallData.toolChoice();
	}

	private void mergeToolCalls(final List<ChatToolCall> currentToolCalls, final List<ChatToolCall> deltaToolCalls) {
		for (final var deltaToolCall : deltaToolCalls) {
			final int index = deltaToolCall.getIndex() != null ? deltaToolCall.getIndex() : currentToolCalls.size();
			while (currentToolCalls.size() <= index) {
				currentToolCalls.add(new ChatToolCall());
			}
			final var currentToolCall = currentToolCalls.get(index);
			if (deltaToolCall.getId() != null && !deltaToolCall.getId().isBlank()) {
				currentToolCall.setId(deltaToolCall.getId());
			}
			if (deltaToolCall.getType() != null && !deltaToolCall.getType().isBlank()) {
				currentToolCall.setType(deltaToolCall.getType());
			}
			if (deltaToolCall.getFunction() != null) {
				if (currentToolCall.getFunction() == null) {
					currentToolCall.setFunction(new ChatToolFunction());
				}
				if (deltaToolCall.getFunction().getName() != null && !deltaToolCall.getFunction().getName().isBlank()) {
					currentToolCall.getFunction().setName(deltaToolCall.getFunction().getName());
				}
				if (deltaToolCall.getFunction().getArguments() != null && !deltaToolCall.getFunction().getArguments().isEmpty()) {
					final var existingArguments = currentToolCall.getFunction().getArguments();
					currentToolCall.getFunction().setArguments((existingArguments == null ? "" : existingArguments) + deltaToolCall.getFunction().getArguments());
				}
			}
		}
	}

	private List<ToolCall> toToolCalls(final List<ChatToolCall> chatToolCalls) {
		if (chatToolCalls == null || chatToolCalls.isEmpty()) {
			return List.of();
		}
		return chatToolCalls.stream()
				.filter(Objects::nonNull)
				.map(toolCall -> new ToolCall(toolCall.getId(), toolCall.getType(), toolCall.getFunction() != null ? toolCall.getFunction().getName() : null, toolCall.getFunction() != null ? toolCall.getFunction().getArguments() : null))
				.toList();
	}
	
	private List<ChatMessage> createChats(final Conversation conversation, final ModelProfile modelProfile) {
		final List<ChatMessage> chat = new ArrayList<>();
		final AtomicInteger tokens = new AtomicInteger();

		for (final Turn turn : conversation.getConversation()) {
			switch (turn.type()) {
				case SYSTEM:
					chat.add(this.createChatMessage("system", turn));
					break;
				case USER:
					if (this.addChatRequestMessage("user", turn, tokens, chat, modelProfile)) {
						return chat;
					}
					break;
				case ASSISTANT:
					if (this.addChatRequestMessage("assistant", turn, tokens, chat, modelProfile)) {
						return chat;
					}
					break;
				case TOOL:
					if (this.addChatRequestMessage("tool", turn, tokens, chat, modelProfile)) {
						return chat;
					}
					break;
				default:
					throw new IllegalArgumentException("Unknown turn type: " + turn.type());
			}
		}

		return chat;
	}
	
	private boolean addChatRequestMessage(final String role, final Turn turn, final AtomicInteger tokens, final List<ChatMessage> chat, final ModelProfile modelProfile) {
		var prompt = turn.text() != null ? turn.text() : "";
		var trimmed = false;
		
		final var textTokens = this.tokenizer.count(prompt, modelProfile);
		final int maxLength = modelProfile.maxContextLength() - modelProfile.maxTokens() - 15; // 15 is a margin for EOT and other special tokens
		if (tokens.addAndGet(textTokens) > maxLength) {
			var tokenized = this.tokenizer.tokenize(prompt, modelProfile);
			LOGGER.warn("Prompt too long: {} tokens / {} max. Prompt will be cut!", tokenized.size(), maxLength);
			tokenized = tokenized.subList(0, tokenized.size() - (tokens.get() - maxLength));
			prompt = this.tokenizer.detokenize(tokenized, modelProfile);
			trimmed = true;
		}
		
		final var message = this.createChatMessage(role, turn);
		message.setContent(prompt);
		chat.add(message);
		
		return trimmed;
	}

	private ChatMessage createChatMessage(final String role, final Turn turn) {
		final var message = new ChatMessage(role, turn.text() != null ? turn.text() : "", null);
		if (!turn.toolCalls().isEmpty()) {
			message.setToolCalls(turn.toolCalls().stream().map(toolCall -> {
				final var function = new ChatToolFunction();
				function.setName(toolCall.name());
				function.setArguments(toolCall.arguments());

				final var chatToolCall = new ChatToolCall();
				chatToolCall.setId(toolCall.id());
				chatToolCall.setType(toolCall.type());
				chatToolCall.setFunction(function);
				return chatToolCall;
			}).toList());
		}
		message.setToolCallId(turn.toolCallId());
		return message;
	}
	
}
