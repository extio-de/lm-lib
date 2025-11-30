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
import com.azure.core.http.policy.RetryPolicy;


import de.extio.lmlib.client.Chunk;
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
import de.extio.lmlib.token.Tokenizer;import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

public final class AzureAiClient implements Client {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AzureAiClient.class);
	
	private static final int TIMEOUT = 300000;
	
	private static final int CONNECT_TIMEOUT = 10000;
	
	private final ConcurrentMap<String, ChatCompletionsClient> chatCompletionsClients = new ConcurrentHashMap<>();
	
	private final ModelProfileService modelProfileService;
	
	private final Tokenizer tokenizer;
	
	private final boolean proxyEnabled;
	
	private final String proxyHost;
	
	private final int proxyPort;
	
	private final String proxyUser;
	
	private final String proxyPassword;
	
	AzureAiClient(final ModelProfileService modelProfileService, final Tokenizer tokenizer, final boolean proxyEnabled, final String proxyHost, final int proxyPort, final String proxyUser, final String proxyPassword) {
		this.modelProfileService = modelProfileService;
		this.tokenizer = tokenizer;
		this.proxyEnabled = proxyEnabled;
		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;
		this.proxyUser = proxyUser;
		this.proxyPassword = proxyPassword;
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
	public Completion conversation(final ModelCategory modelCategory, final Conversation conversation) {
		return this.requestConversation(modelCategory, null, conversation, null);
	}
	
	@Override
	public Completion conversation(final ModelProfile modelProfile, final Conversation conversation) {
		return this.requestConversation(null, modelProfile, conversation, null);
	}
	
	@Override
	public Completion streamConversation(final ModelCategory modelCategory, final Conversation conversation, final Consumer<Chunk> chunkConsumer) {
		return this.requestConversation(modelCategory, null, conversation, chunkConsumer);
	}
	
	@Override
	public Completion streamConversation(final ModelProfile modelProfile, final Conversation conversation, final Consumer<Chunk> chunkConsumer) {
		return this.requestConversation(null, modelProfile, conversation, chunkConsumer);
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
	
	private Completion requestConversation(ModelCategory modelCategory, final ModelProfile modelProfile, final Conversation conversation, final Consumer<Chunk> chunkConsumer) {
		final ModelProfile profile;
		if (modelProfile != null) {
			profile = modelProfile;
			if (modelCategory == null) {
				modelCategory = new ModelCategory(modelProfile.modelName(), modelProfile.category());
			}
		}
		else {
			if (modelCategory == null) {
				modelCategory = ModelCategory.MEDIUM;
			}
			profile = this.modelProfileService.getModelProfile(modelCategory.getModelProfile(), modelCategory);
		}
		
		if (profile == null || profile.modelProvider() != ModelProvider.AZURE_AI) {
			throw new IllegalArgumentException("Invalid ModelProfile");
		}
		
		final var chat = this.createChats(conversation, profile);
		
		if (chunkConsumer == null) {
			return this.requestCompletions(chat, profile);
		}
		else {
			return this.requestStreamCompletions(chat, profile, chunkConsumer);
		}
	}

	private Completion requestCompletions(final List<ChatRequestMessage> chat, final ModelProfile modelProfile) {
		LOGGER.debug("Requesting ChatCompletion at {}", modelProfile.modelName());
		final LocalDateTime start = LocalDateTime.now();
		
		final var options = this.createChatCompletionsOptions(chat, modelProfile);
		final var client = this.createHttpClient(modelProfile);
		final var response = client.complete(options);
		
		final var finishReason = this.mapFinishReason(response.getChoice().getFinishReason());
		final var statistics = this.createStatistics(start, response.getUsage().getPromptTokens(), response.getUsage().getCompletionTokens(), modelProfile);
		
		return new Completion(response.getChoice().getMessage().getContent(), null, finishReason, statistics);
	}
	
	private Completion requestStreamCompletions(final List<ChatRequestMessage> chat, final ModelProfile modelProfile, final Consumer<Chunk> chunkConsumer) {
		LOGGER.debug("Requesting Streaming ChatCompletion at {}", modelProfile.modelName());
		final LocalDateTime start = LocalDateTime.now();
		
		final var options = this.createChatCompletionsOptions(chat, modelProfile);
		final var client = this.createHttpClient(modelProfile);
		final var completionsStream = client.completeStream(options);
		
		final StringBuilder contentBuilder = new StringBuilder();
		final AtomicInteger promptTokens = new AtomicInteger(0);
		final AtomicInteger completionTokens = new AtomicInteger(0);
		final CompletionFinishReason[] finishReasonHolder = {CompletionFinishReason.DONE};
		
		completionsStream.forEach(streamResponse -> {
			if (streamResponse.getChoices() != null && !streamResponse.getChoices().isEmpty()) {
				final var choice = streamResponse.getChoices().get(0);
				
				if (choice.getFinishReason() != null) {
					finishReasonHolder[0] = this.mapFinishReason(choice.getFinishReason());
				}
				
				if (choice.getDelta() != null && choice.getDelta().getContent() != null) {
					final String content = choice.getDelta().getContent();
					contentBuilder.append(content);
					chunkConsumer.accept(new Chunk(content, null));
				}
			}
			
			if (streamResponse.getUsage() != null) {
				promptTokens.set(streamResponse.getUsage().getPromptTokens());
				completionTokens.set(streamResponse.getUsage().getCompletionTokens());
			}
		});
		
		final var statistics = this.createStatistics(start, promptTokens.get(), completionTokens.get(), modelProfile);
		
		return new Completion(contentBuilder.toString(), null, finishReasonHolder[0], statistics);
	}
	
	private ChatCompletionsOptions createChatCompletionsOptions(final List<ChatRequestMessage> chat, final ModelProfile modelProfile) {
		final var options = new ChatCompletionsOptions(chat);
		options.setModel(modelProfile.modelName());
		options.setMaxTokens(modelProfile.maxTokens());
		options.setTemperature(modelProfile.temperature());
		options.setTopP(modelProfile.topP());
		return options;
	}
	
	private CompletionFinishReason mapFinishReason(final CompletionsFinishReason azureFinishReason) {
		if (CompletionsFinishReason.CONTENT_FILTERED.equals(azureFinishReason)) {
			return CompletionFinishReason.CONTENT_FILTERED;
		}
		else if (CompletionsFinishReason.TOKEN_LIMIT_REACHED.equals(azureFinishReason)) {
			return CompletionFinishReason.TOKEN_LIMIT_REACHED;
		}
		else {
			return CompletionFinishReason.DONE;
		}
	}
	
	private CompletionStatistics createStatistics(final LocalDateTime start, final int promptTokens, final int completionTokens, final ModelProfile modelProfile) {
		final var statistics = new CompletionStatistics(
				1,
				Duration.between(start, LocalDateTime.now()),
				promptTokens,
				0,
				completionTokens,
				0,
				new BigDecimal(promptTokens).multiply(modelProfile.costPerInToken()).add(new BigDecimal(completionTokens).multiply(modelProfile.costPerOutToken())),
				false);
		LOGGER.debug("{}", statistics);
		return statistics;
	}
	
	private ChatCompletionsClient createHttpClient(final ModelProfile modelProfile) {
		return this.chatCompletionsClients.computeIfAbsent(modelProfile.category(), key -> {
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
					
			reactor.netty.http.client.HttpClient nettyHttpClient = reactor.netty.http.client.HttpClient.create()
					.option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT)
					.responseTimeout(Duration.ofMillis(TIMEOUT))
					.doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(TIMEOUT, TimeUnit.MILLISECONDS)).addHandlerLast(new WriteTimeoutHandler(TIMEOUT, TimeUnit.MILLISECONDS)));
			
			if (this.proxyEnabled) {
				LOGGER.info("Proxy is enabled. Configuring proxy settings for host: {} port: {} user: {}", this.proxyHost, this.proxyPort, this.proxyUser);
				nettyHttpClient = nettyHttpClient.proxy(proxy -> proxy
						.type(reactor.netty.transport.ProxyProvider.Proxy.HTTP)
						.host(this.proxyHost)
						.port(this.proxyPort)
						.username(this.proxyUser)
						.password(password -> this.proxyPassword));
			}
			else {
				LOGGER.info("Proxy is not enabled. Skipping proxy configuration.");
			}
			
			final HttpClient azureHttpClient = new NettyAsyncHttpClientBuilder(nettyHttpClient)
				.connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT))
				.readTimeout(Duration.ofMillis(TIMEOUT))
				.writeTimeout(Duration.ofMillis(TIMEOUT))
				.responseTimeout(Duration.ofMillis(TIMEOUT))
				.build();
			
			final ChatCompletionsClient chatCompletionsClient = new ChatCompletionsClientBuilder()
					.credential(new AzureKeyCredential(modelProfile.apiKey()))
					.endpoint(modelProfile.url())
					.retryPolicy(new RetryPolicy(retryOptions))
					.httpClient(azureHttpClient)
					.buildClient();
			return chatCompletionsClient;
		});
	}
	
	private boolean shouldRetryError(final int statusCode) {
		return statusCode == 408 || statusCode == 429 || statusCode >= 500 && statusCode < 600;
	}
	
}
