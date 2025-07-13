package de.extio.lmlib.client.azureai;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.azure.ai.inference.ChatCompletionsClient;
import com.azure.ai.inference.ChatCompletionsClientBuilder;
import com.azure.ai.inference.models.ChatCompletionsOptions;
import com.azure.ai.inference.models.ChatRequestAssistantMessage;
import com.azure.ai.inference.models.ChatRequestMessage;
import com.azure.ai.inference.models.ChatRequestSystemMessage;
import com.azure.ai.inference.models.ChatRequestUserMessage;
import com.azure.ai.inference.models.CompletionsFinishReason;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.azure.core.http.policy.ExponentialBackoffOptions;
import com.azure.core.http.policy.RetryOptions;

import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.CompletionStatistics;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;
import de.extio.lmlib.profile.ModelProfileService;
import de.extio.lmlib.token.Tokenizer;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

public final class AzureAiClient implements Client {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AzureAiClient.class);
	
	private static final int TIMEOUT = 300000;
	
	private static final int CONNECT_TIMEOUT = 10000;
	
	private final ConcurrentMap<ModelCategory, ChatCompletionsClient> chatCompletionsClients = new ConcurrentHashMap<>();
	
	private final ModelProfileService modelProfileService;
	
	private final Tokenizer tokenizer;
	
	AzureAiClient(final ModelProfileService modelProfileService, final Tokenizer tokenizer) {
		this.modelProfileService = modelProfileService;
		this.tokenizer = tokenizer;
	}
	
	@Override
	public ModelProvider getModelProvider() {
		return ModelProvider.AZURE_AI;
	}
	
	@Override
	public Completion completion(final ModelCategory modelCategory, final String system, final String text) {
		return this.conversation(modelCategory, Conversation.create(system, text));
	}
	
	@Override
	public Completion conversation(ModelCategory modelCategory, final Conversation conversation) {
		if (modelCategory == null) {
			modelCategory = ModelCategory.MEDIUM;
		}
		final var modelProfile = this.modelProfileService.getModelProfile(modelCategory.getModelProfile());
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.AZURE_AI) {
			throw new IllegalArgumentException("ModelProfile " + modelCategory.getModelProfile());
		}
		
		final var chat = this.createChats(conversation, modelProfile);
		
		return this.requestCompletions(chat, modelCategory, modelProfile);
	}
	
	@Override
	public Completion streamConversation(final ModelCategory modelCategory, final Conversation conversation, final Consumer<String> chunkConsumer) {
		throw new UnsupportedOperationException("Not implemented yet");
	}
	
	private List<ChatRequestMessage> createChats(final Conversation conversation, final ModelProfile modelProfile) {
		final List<ChatRequestMessage> chat = new ArrayList<>();
		
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
					chat.add(new ChatRequestSystemMessage(systemTurn.text()));
				}
				if (this.addChatRequestMessage(userTurn.text(), tokens, chat, modelProfile, ChatRequestUserMessage::new)) {
					break;
				}
				userTurn = null;
				first = false;
			}
			else if (!first && assistantTurn != null && userTurn != null) {
				if (this.addChatRequestMessage(assistantTurn.text(), tokens, chat, modelProfile, ChatRequestAssistantMessage::new)) {
					break;
				}
				if (this.addChatRequestMessage(userTurn.text(), tokens, chat, modelProfile, ChatRequestUserMessage::new)) {
					break;
				}
				userTurn = null;
				assistantTurn = null;
			}
		}
		
		return chat;
	}
	
	private boolean addChatRequestMessage(final String text, final AtomicInteger tokens, final List<ChatRequestMessage> chat, final ModelProfile modelProfile, final Function<String, ChatRequestMessage> messageFactory) {
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
		
		chat.add(messageFactory.apply(prompt));
		
		return trimmed;
	}
	
	private Completion requestCompletions(final List<ChatRequestMessage> chat, final ModelCategory modelCategory, final ModelProfile modelProfile) {
		LOGGER.debug("Requesting ChatCompletion at {}", modelProfile.modelName());
		final LocalDateTime start = LocalDateTime.now();
		
		final var options = new ChatCompletionsOptions(chat);
		options.setModel(modelProfile.modelName());
		options.setMaxTokens(modelProfile.maxTokens());
		options.setTemperature(modelProfile.temperature());
		options.setTopP(modelProfile.topP());
		
		final var client = this.createHttpClient(modelCategory, modelProfile);
		final var response = client.complete(options);
		
		final CompletionFinishReason finishReason;
		if (CompletionsFinishReason.CONTENT_FILTERED.equals(response.getChoice().getFinishReason())) {
			finishReason = CompletionFinishReason.CONTENT_FILTERED;
		}
		else if (CompletionsFinishReason.TOKEN_LIMIT_REACHED.equals(response.getChoice().getFinishReason())) {
			finishReason = CompletionFinishReason.TOKEN_LIMIT_REACHED;
		}
		else {
			finishReason = CompletionFinishReason.DONE;
		}
		
		final var statistics = new CompletionStatistics(
				1,
				Duration.between(start, LocalDateTime.now()),
				response.getUsage().getPromptTokens(),
				response.getUsage().getCompletionTokens(),
				new BigDecimal(response.getUsage().getPromptTokens()).multiply(modelProfile.costInToken()).add(new BigDecimal(response.getUsage().getCompletionTokens()).multiply(modelProfile.costOutToken())),
				false);
		LOGGER.debug("{}", statistics);
		
		return new Completion(response.getChoice().getMessage().getContent(), finishReason, statistics);
	}
	
	private ChatCompletionsClient createHttpClient(final ModelCategory modelCategory, final ModelProfile modelProfile) {
		return this.chatCompletionsClients.computeIfAbsent(modelCategory, key -> {
			final ExponentialBackoffOptions exponentialOptions = new ExponentialBackoffOptions()
					.setMaxRetries(99)
					.setBaseDelay(Duration.ofSeconds(1))
					.setMaxDelay(Duration.ofSeconds(10));
			
			final RetryOptions retryOptions = new RetryOptions(exponentialOptions)
					.setShouldRetryCondition(httpResponse -> {
						if (httpResponse != null) {
							if (httpResponse.getThrowable() != null) {
								return true;
							}
							if (httpResponse.getResponse() != null) {
								return this.shouldRetryError(httpResponse.getResponse().getStatusCode());
							}
						}
						return false;
					});
					
			final reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
					.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT)
					.responseTimeout(Duration.ofMillis(TIMEOUT))
					.doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(TIMEOUT, TimeUnit.MILLISECONDS)).addHandlerLast(new WriteTimeoutHandler(TIMEOUT, TimeUnit.MILLISECONDS)));
			
			final NettyAsyncHttpClientBuilder azureHttpClientBuilder = new NettyAsyncHttpClientBuilder(httpClient);
			final HttpClient azureHttpClient = azureHttpClientBuilder
					.readTimeout(Duration.ofMillis(TIMEOUT))
					.responseTimeout(Duration.ofMillis(TIMEOUT))
					.writeTimeout(Duration.ofMillis(TIMEOUT))
					.build();
			
			final ChatCompletionsClient chatCompletionsClient = new ChatCompletionsClientBuilder()
					.credential(new AzureKeyCredential(modelProfile.apiKey()))
					.endpoint(modelProfile.url())
					.retryOptions(retryOptions)
					.httpClient(azureHttpClient)
					.buildClient();
			return chatCompletionsClient;
		});
	}
	
	private boolean shouldRetryError(final int statusCode) {
		return statusCode == 408 || statusCode == 429 || (statusCode >= 500 && statusCode < 600);
	}
	
}
