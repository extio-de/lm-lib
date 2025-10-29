package de.extio.lmlib.client.oai.completion.text;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.oai.completion.AbstractCompletionClient;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;
import de.extio.lmlib.prompt.PromptStrategy;
import de.extio.lmlib.prompt.PromptStrategyFactory;
import reactor.core.publisher.Mono;

@Component
public class TextCompletionClient extends AbstractCompletionClient {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TextCompletionClient.class);
	
	@Autowired
	private PromptStrategyFactory promptStrategyFactory;
	
	@Override
	public ModelProvider getModelProvider() {
		return ModelProvider.OAI_TEXT_COMPLETION;
	}
	
	@Override
	protected Completion requestCompletion(final Conversation conversation, final ModelProfile modelProfile, final Consumer<Chunk> chunkConsumer) {
		final var promptStrategy = this.promptStrategyFactory.getStrategy(modelProfile.prompt());
		if (promptStrategy == null) {
			throw new IllegalArgumentException("Prompt strategy not found: " + modelProfile.prompt());
		}
		
		final var prompt = this.createPrompt(conversation, modelProfile, promptStrategy);
		
		final var request = new CompletionRequest();
		request.setModel(this.modelNameSupplier.getModelName(modelProfile));
		request.setPrompt(prompt);
		request.setMaxTokens(modelProfile.maxTokens());
		request.setTemperature(modelProfile.temperature());
		request.setTopP(modelProfile.topP());
		request.setStream(chunkConsumer != null);
		if (chunkConsumer != null) {
			this.configureStreamOptions(true, request::setStreamOptions);
		}
		
		String requestBody;
		try {
			requestBody = this.objectMapper.writeValueAsString(request);
		}
		catch (final JsonProcessingException e) {
			throw new IllegalStateException("Cannot serialize request body", e);
		}
		
		LOGGER.debug("Requesting completion at {}", modelProfile.url());
		final LocalDateTime start = LocalDateTime.now();
		final var webClient = this.webClientBuilder.baseUrl(modelProfile.url()).build();
		final var response = webClient
				.method(HttpMethod.POST)
				.uri(uriBuilder -> uriBuilder.path("/v1/completions").build())
				.header("Content-Type", "application/json")
				.bodyValue(requestBody)
				.exchangeToMono(clientResponse -> {
					if (clientResponse.statusCode().isError()) {
						return clientResponse.bodyToMono(String.class)
								.flatMap(errorBody -> Mono.error(new IllegalStateException("Error response from server: " + clientResponse.statusCode() + " - " + errorBody)));
					}
					
					if (chunkConsumer == null) {
						return clientResponse.bodyToMono(CompletionResponse.class);
					}
					else {
						final var CompletionResponse = new CompletionResponse();
						CompletionResponse.setChoices(List.of(new Choice()));
						final StringBuilder contentStringBuilder = new StringBuilder();
						return clientResponse.bodyToFlux(String.class)
								.doOnNext(buffer -> {
									try {
										if (!"[DONE]".equals(buffer)) {
											final var streamResponse = this.objectMapper.readValue(buffer, CompletionResponse.class);
											
											if (streamResponse.getId() != null && !streamResponse.getId().isEmpty()) {
												CompletionResponse.setId(streamResponse.getId());
											}
											if (streamResponse.getObject() != null && !streamResponse.getObject().isEmpty()) {
												CompletionResponse.setObject(streamResponse.getObject());
											}
											if (streamResponse.getCreated() != null) {
												CompletionResponse.setCreated(streamResponse.getCreated());
											}
											if (streamResponse.isStoppedEos() != null) {
												CompletionResponse.setStoppedEos(streamResponse.isStoppedEos());
											}
											if (streamResponse.getContent() != null && !streamResponse.getContent().isEmpty()) {
												contentStringBuilder.append(streamResponse.getContent());
											}
											if (streamResponse.getUsage() != null) {
												CompletionResponse.setUsage(streamResponse.getUsage());
											}
											if (streamResponse.getChoices() != null && !streamResponse.getChoices().isEmpty()) {
												if (streamResponse.getChoices().getFirst().getFinishReason() != null) {
													CompletionResponse.getChoices().getFirst().setFinishReason(streamResponse.getChoices().getFirst().getFinishReason());
												}
												if (streamResponse.getChoices().getFirst().getScore() != null) {
													CompletionResponse.getChoices().getFirst().setScore(streamResponse.getChoices().getFirst().getScore());
												}
												if (streamResponse.getChoices().getFirst().getText() != null && !streamResponse.getChoices().getFirst().getText().isEmpty()) {
													contentStringBuilder.append(streamResponse.getChoices().getFirst().getText());
													chunkConsumer.accept(new Chunk(streamResponse.getChoices().getFirst().getText(), null));
												}
											}
										}
									}
									catch (final JsonProcessingException e) {
										LOGGER.warn("Streamed chunk is not parseable", e);
									}
								})
								.then(Mono.<CompletionResponse> fromSupplier(() -> {
									CompletionResponse.getChoices().getFirst().setText(contentStringBuilder.toString());
									return CompletionResponse;
								}));
					}
				})
				.retryWhen(this.createRetrySpec())
				.block();
		
		String content = null;
		String reasoning = null;
		CompletionFinishReason finishReason = null;
		if (response.getChoices() != null && !response.getChoices().isEmpty()) {
			final var choice = response.getChoices().getFirst();
			if (response.getChoices().getFirst().getText() != null) {
				content = promptStrategy.getResponse(choice.getText());
				reasoning = promptStrategy.getReasoning(choice.getText());
			}
			if (response.getChoices().getFirst().getFinishReason() != null) {
				finishReason = this.mapFinishReason(choice.getFinishReason());
			}
		}
		if (content == null && response.getContent() != null) {
			content = promptStrategy.getResponse(response.getContent());
			reasoning = promptStrategy.getReasoning(response.getContent());
		}
		if (content == null) {
			throw new IllegalStateException("No parseable response content from server");
		}
		content = promptStrategy.removeEOT(content);
		if (reasoning != null) {
			reasoning = promptStrategy.removeEOT(reasoning);
		}
		if (finishReason == null) {
			if (response.isStoppedEos() != null) {
				finishReason = Boolean.TRUE.equals(response.isStoppedEos()) ? CompletionFinishReason.DONE : CompletionFinishReason.TOKEN_LIMIT_REACHED;
			}
			else {
				finishReason = CompletionFinishReason.DONE;
			}
		}
		
		final var statistics = createCompletionStatistics(modelProfile, start, response.getUsage(), prompt, response.getContent());
		return new Completion(content, reasoning, finishReason, statistics);
	}
	
	private String createPrompt(final Conversation conversation, final ModelProfile modelProfile, final PromptStrategy promptStrategy) {
		StringBuilder prompt = this.formatConversation(conversation, promptStrategy);
		
		List<Long> tokenized = this.tokenizer.tokenize(prompt.toString(), modelProfile);
		final int maxLength = modelProfile.maxContextLength() - modelProfile.maxTokens() - 15; // 15 is a margin for EOT and other special tokens
		if (tokenized.size() > maxLength) {
			LOGGER.warn("Prompt too long: {} tokens / {} max. Prompt will be cut!", tokenized.size(), maxLength);
			tokenized = tokenized.subList(0, maxLength);
			prompt = new StringBuilder(this.tokenizer.detokenize(tokenized, modelProfile));
		}
		
		LOGGER.debug("Completion request for {}. Input tokens: {}", modelProfile.category(), tokenized.size());
		
		return prompt.toString();
	}
	
	private StringBuilder formatConversation(final Conversation conversation, final PromptStrategy promptStrategy) {
		final StringBuilder prompt = new StringBuilder();
		
		final Deque<Turn> turns = new ArrayDeque<>(conversation.getConversation());
		Turn systemTurn = null;
		Turn userTurn = null;
		Turn assistantTurn = null;
		boolean first = true;
		
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
				prompt.append(promptStrategy.start(systemTurn != null ? systemTurn.text() : "", userTurn.text()));
				userTurn = null;
				first = false;
			}
			else if (!first && assistantTurn != null && userTurn != null) {
				promptStrategy.next(prompt, assistantTurn.text(), userTurn.text());
				userTurn = null;
				assistantTurn = null;
			}
		}
		
		return prompt;
	}
	
}
