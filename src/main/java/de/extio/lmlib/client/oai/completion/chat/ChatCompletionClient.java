package de.extio.lmlib.client.oai.completion.chat;

import java.time.Duration;
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

import com.fasterxml.jackson.core.JsonProcessingException;

import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.oai.completion.AbstractCompletionClient;
import de.extio.lmlib.client.oai.completion.FinishReasons;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

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
	protected Completion requestCompletion(final Conversation conversation, final ModelCategory modelCategory, final ModelProfile modelProfile, final Consumer<String> chunkConsumer) {
		final var chat = createChats(conversation, modelProfile);
		
		final var request = new ChatCompletionRequest();
		request.setModel(this.modelNameSupplier.getModelName(modelProfile.url()));
		request.setMessages(chat);
		request.setMaxTokens(modelProfile.maxTokens());
		request.setMaxCompletionTokens(modelProfile.maxTokens());
		request.setTemperature(modelProfile.temperature());
		request.setTopP(modelProfile.topP());
		if (chunkConsumer == null) {
			request.setStream(false);
		}
		else {
			request.setStream(true);
			request.setStreamOptions(new ChatCompletionStreamOptions());
			request.getStreamOptions().setIncludeUsage(true);
		}
		
		LOGGER.debug("Requesting chat completion at {}", modelProfile.url());
		final LocalDateTime start = LocalDateTime.now();
		final var webClient = this.webClientBuilder.baseUrl(modelProfile.url()).build();
		
		final var response = webClient
				.method(HttpMethod.POST)
				.uri(uriBuilder -> uriBuilder.path("/v1/chat/completions").build())
				.header("Content-Type", "application/json")
				.bodyValue(request)
				.exchangeToMono(clientResponse -> {
					if (chunkConsumer == null) {
						return clientResponse.bodyToMono(ChatCompletionResponse.class);
					}
					else {
						final var chatCompletionResponse = new ChatCompletionResponse();
						chatCompletionResponse.setChoices(List.of(new ChatChoice()));
						final StringBuilder contenStringBuilder = new StringBuilder();
						return clientResponse.bodyToFlux(String.class)
								.doOnNext(buffer -> {
									try {
										if (! "[DONE]".equals(buffer)) {
											final var streamResponse = this.objectMapper.readValue(buffer, ChatCompletionResponse.class);
											
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
											if (streamResponse.getChoices() != null && !streamResponse.getChoices().isEmpty()) {
												if (streamResponse.getChoices().getFirst().getFinishReason() != null) {
													chatCompletionResponse.getChoices().getFirst().setFinishReason(streamResponse.getChoices().getFirst().getFinishReason());
												}
												if (streamResponse.getChoices().getFirst().getMessage() != null && streamResponse.getChoices().getFirst().getMessage().getContent() != null && !streamResponse.getChoices().getFirst().getMessage().getContent().isEmpty()) {
													contenStringBuilder.append(streamResponse.getChoices().getFirst().getMessage().getContent());
													chunkConsumer.accept(streamResponse.getChoices().getFirst().getMessage().getContent());
												}
											}
										}
									}
									catch (final JsonProcessingException e) {
										LOGGER.warn("Streamed chunk is not parseable", e);
									}
								})
								.then(Mono.<ChatCompletionResponse> fromSupplier(() -> {
									chatCompletionResponse.getChoices().getFirst().setMessage(new ChatMessage("assistant", contenStringBuilder.toString()));
									return chatCompletionResponse;
								}));
					}
				})
				.retryWhen(Retry.backoff(99, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(10)).jitter(0.25d).doAfterRetry(rs -> LOGGER.warn("Request failed " + rs.failure().getMessage())))
				.block();
		
		if (response.getChoices() == null || response.getChoices().isEmpty() || response.getChoices().getFirst().getMessage() == null) {
			throw new IllegalStateException("No parseable response content from server");
		}
		
		final var choice = response.getChoices().getFirst();
		final var content = response.getChoices().getFirst().getMessage().getContent();
		final var finishReason = switch (choice.getFinishReason()) {
			case FinishReasons.FINISH_REASON_CONTENT_FILTER -> CompletionFinishReason.CONTENT_FILTERED;
			case FinishReasons.FINISH_REASON_LENGTH -> CompletionFinishReason.TOKEN_LIMIT_REACHED;
			default -> CompletionFinishReason.DONE;
		};
		final var statistics = createCompletionStatistics(modelProfile, start, response.getUsage(), null, content);
		return new Completion(content, finishReason, statistics);
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
					chat.add(new ChatMessage("system", systemTurn.text()));
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
		
		chat.add(new ChatMessage(role, prompt));
		
		return trimmed;
	}
	
}
