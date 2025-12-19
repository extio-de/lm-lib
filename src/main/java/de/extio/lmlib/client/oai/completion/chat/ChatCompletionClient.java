package de.extio.lmlib.client.oai.completion.chat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.core.JacksonException;

import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.oai.completion.AbstractCompletionClient;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;

@Component
public class ChatCompletionClient extends AbstractCompletionClient {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ChatCompletionClient.class);
	
	public ChatCompletionClient() {
		super();
	}
	
	@Override
	public ModelProvider getModelProvider() {
		return ModelProvider.OAI_CHAT_COMPLETION;
	}
	
	@Override
	protected Completion requestCompletion(final Conversation conversation, final ModelProfile modelProfile, final Consumer<Chunk> chunkConsumer) {
		final var chat = createChats(conversation, modelProfile);
		
		final var request = new ChatCompletionRequest();
		request.setModel(this.modelNameSupplier.getModelName(modelProfile));
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
										}
									}
								}
								catch (final JacksonException e) {
									LOGGER.warn("Streamed chunk is not parseable: {}", line, e);
								}
							}
						}
						chatCompletionResponse.getChoices().getFirst().setMessage(new ChatMessage("assistant", contentStringBuilder.toString(), reasoningStringBuilder.toString()));
						return chatCompletionResponse;
					}
				});
		
		if (response.getChoices() == null || response.getChoices().isEmpty() || response.getChoices().getFirst().getMessage() == null) {
			throw new IllegalStateException("No parseable response content from server");
		}
		final var choice = response.getChoices().getFirst();
		final var content = response.getChoices().getFirst().getMessage().getContent();
		final var reasoning = response.getChoices().getFirst().getMessage().getReasoningContent();
		final var finishReason = this.mapFinishReason(choice.getFinishReason());
		final var statistics = createCompletionStatistics(modelProfile, start, response.getUsage(), response.getTimings(), null, content, reasoning);
		return new Completion(content, reasoning, finishReason, statistics);
	}
	
	private List<ChatMessage> createChats(final Conversation conversation, final ModelProfile modelProfile) {
		final List<ChatMessage> chat = new ArrayList<>();
		
		final Deque<Turn> turns = new ArrayDeque<>(conversation.getConversation());
		Turn systemTurn = null;
		Turn userTurn = null;
		Turn assistantTurn = null;
		boolean first = true;
		
		final AtomicInteger tokens = new AtomicInteger();
		
		while (!turns.isEmpty()) {
			final Turn turn = turns.pop();
			switch (turn.type()) {
				case SYSTEM:
					systemTurn = turn;
					break;
				case USER:
					userTurn = turn;
					break;
				case ASSISTANT:
					assistantTurn = turn;
					break;
				default:
					throw new IllegalArgumentException("Unknown turn type: " + turn.type());
			}
			
			if (first && userTurn != null) {
				if (systemTurn != null) {
					chat.add(new ChatMessage("system", systemTurn.text(), null));
				}
				if (this.addChatRequestMessage("user", userTurn.text(), tokens, chat, modelProfile)) {
					break;
				}
				userTurn = null;
				first = false;
			}
			else if (!first && assistantTurn != null && userTurn != null) {
				if (this.addChatRequestMessage("assistant", assistantTurn.text(), tokens, chat, modelProfile)) {
					break;
				}
				if (this.addChatRequestMessage("user", userTurn.text(), tokens, chat, modelProfile)) {
					break;
				}
				userTurn = null;
				assistantTurn = null;
			}
		}
		
		return chat;
	}
	
	private boolean addChatRequestMessage(final String role, final String text, final AtomicInteger tokens, final List<ChatMessage> chat, final ModelProfile modelProfile) {
		var prompt = text;
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
		
		chat.add(new ChatMessage(role, prompt, null));
		
		return trimmed;
	}
	
}
