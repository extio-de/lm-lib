package de.extio.lmlib.client.azureai;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.azure.ai.inference.ChatCompletionsClient;
import com.azure.ai.inference.ChatCompletionsClientBuilder;
import com.azure.ai.inference.models.ChatCompletions;
import com.azure.ai.inference.models.ChatCompletionsOptions;
import com.azure.ai.inference.models.ChatRequestAssistantMessage;
import com.azure.ai.inference.models.ChatRequestMessage;
import com.azure.ai.inference.models.ChatRequestSystemMessage;
import com.azure.ai.inference.models.ChatRequestUserMessage;
import com.azure.ai.inference.models.CompletionsFinishReason;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.policy.ExponentialBackoffOptions;
import com.azure.core.http.policy.RetryOptions;

import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.profile.ModelCategory;
import de.extio.lmlib.client.profile.ModelProfile;
import de.extio.lmlib.client.profile.ModelProfile.ModelProvider;
import de.extio.lmlib.client.profile.ModelProfileService;
import de.extio.lmlib.client.token.Tokenizer;

public final class AzureAiClient implements Client {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AzureAiClient.class);
	
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
	public Completion completion(final ModelCategory modelCategory, final String system, final String question, final String fullText) {
		String text = "";
		if (!question.isEmpty() && !fullText.isEmpty()) {
			text = question + "\n" + fullText;
		}
		else if (!question.isEmpty()) {
			text = question;
		}
		else if (!fullText.isEmpty()) {
			text = fullText;
		}
		else {
			throw new IllegalArgumentException("Either question or fullText must be set");
		}
		
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
		
		final List<List<ChatRequestMessage>> chats = this.createChats(conversation, modelProfile);
		
		final List<StringBuilder> answers = new ArrayList<>();
		final CompletionStatistics statistics = new CompletionStatistics();
		for (final List<ChatRequestMessage> chat : chats) {
			final StringBuilder answer = new StringBuilder();
			this.requestCompletionsContinuations(chat, answer, statistics, modelCategory, modelProfile);
			answers.add(answer);
		}
		
		// TODO: Reactivate when multiple (split) prompts have been implemented again
		//		if (answers.size() > 1) {
		//			this.summarize(answers, modelProfile, promptStrategy, statistics);
		//		}
		
		return new Completion(
				answers.stream().collect(Collectors.joining()),
				statistics.requests,
				statistics.duration,
				statistics.inTokens,
				statistics.outTokens,
				false);
	}
	
	private List<List<ChatRequestMessage>> createChats(final Conversation conversation, final ModelProfile modelProfile) {
		final List<ChatRequestMessage> chat = new ArrayList<>();
		
		final Deque<Turn> turns = new ArrayDeque<>(conversation.getConversation());
		Turn systemTurn = null;
		Turn userTurn = null;
		Turn assistantTurn = null;
		boolean first = true;
		
		final int maxLength = modelProfile.maxContextLength() - modelProfile.maxTokens() - 50; // 50 is a margin for EOT and other special tokens
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
				if (this.addChatRequestMessage(userTurn.text(), maxLength, tokens, chat, modelProfile, ChatRequestUserMessage::new)) {
					break;
				}
				userTurn = null;
				first = false;
			}
			else if (!first && assistantTurn != null && userTurn != null) {
				if (this.addChatRequestMessage(assistantTurn.text(), maxLength, tokens, chat, modelProfile, ChatRequestAssistantMessage::new)) {
					break;
				}
				if (this.addChatRequestMessage(userTurn.text(), maxLength, tokens, chat, modelProfile, ChatRequestUserMessage::new)) {
					break;
				}
				userTurn = null;
				assistantTurn = null;
			}
		}
		
		return List.of(chat);
	}
	
	private boolean addChatRequestMessage(final String text, final int maxLength, final AtomicInteger tokens, final List<ChatRequestMessage> chat, final ModelProfile modelProfile, final Function<String, ChatRequestMessage> messageFactory) {
		final int textTokens = this.tokenizer.count(text, modelProfile);
		
		String trimmedText;
		boolean trimmed = false;
		if (tokens.addAndGet(textTokens) > maxLength) {
			// TODO: Cut smarter, e.g. split conversation into parts while keeping system prompt
			LOGGER.warn("Prompt too long: {} tokens / {} max. Prompt will be cut!", tokens.get(), maxLength);
			trimmedText = text.substring(0, text.length() - (tokens.get() - maxLength) / 3);
			trimmed = true;
		}
		else {
			trimmedText = text;
		}
		
		chat.add(messageFactory.apply(trimmedText));
		
		return trimmed;
	}
	
	private void requestCompletionsContinuations(final List<ChatRequestMessage> chat, final StringBuilder answer, final CompletionStatistics statistics, final ModelCategory modelCategory, final ModelProfile modelProfile) {
		for (int continuation = 0; continuation < modelProfile.maxContinuations(); continuation++) {
			final LocalDateTime start = LocalDateTime.now();
			
			final var response = this.requestCompletions(chat, modelCategory, modelProfile);
			
			final var choice = response.getChoice();
			
			statistics.add(Duration.between(start, LocalDateTime.now()), response.getUsage().getPromptTokens(), response.getUsage().getCompletionTokens());
			
			answer.append(choice.getMessage().getContent());
			
			final var complete = !(choice.getFinishReason() == CompletionsFinishReason.TOKEN_LIMIT_REACHED);
			if (complete) {
				LOGGER.debug("Prompt response complete");
				break;
			}
			
			answer.append('\n');
			chat.add(new ChatRequestAssistantMessage(choice.getMessage().getContent()));
			chat.add(new ChatRequestUserMessage("continue"));
		}
	}
	
	private ChatCompletions requestCompletions(final List<ChatRequestMessage> chat, final ModelCategory modelCategory, final ModelProfile modelProfile) {
		LOGGER.debug("Requesting ChatCompletion at {}", modelProfile.modelName());
		
		final var client = this.chatCompletionsClients.computeIfAbsent(modelCategory, key -> {
			final ExponentialBackoffOptions exponentialOptions = new ExponentialBackoffOptions()
					.setMaxRetries(99)
					.setBaseDelay(Duration.ofSeconds(1))
					.setMaxDelay(Duration.ofSeconds(10));
			final RetryOptions retryOptions = new RetryOptions(exponentialOptions);
			
			final ChatCompletionsClient chatCompletionsClient = new ChatCompletionsClientBuilder()
					.credential(new AzureKeyCredential(modelProfile.apiKey()))
					.endpoint(modelProfile.url())
					.retryOptions(retryOptions)
					.buildClient();
			return chatCompletionsClient;
		});
		
		final var options = new ChatCompletionsOptions(chat);
		options.setModel(modelProfile.modelName());
		options.setMaxTokens(modelProfile.maxTokens());
		options.setTemperature(modelProfile.temperature());
		options.setTopP(modelProfile.topP());
		
		return client.complete(options);
	}
	
	static class CompletionStatistics {
		
		int requests;
		
		Duration duration = Duration.ofMillis(0l);
		
		long inTokens;
		
		long outTokens;
		
		void add(final Duration duration, final int inTokens, final int outTokens) {
			LOGGER.info("Request duration: {}; in: {}; out: {}; tps: {}", duration, inTokens, outTokens, String.format("%.2f", (inTokens + outTokens) / (double) duration.toMillis() * 1000.0));
			
			this.requests++;
			this.duration = this.duration.plus(duration);
			this.inTokens += inTokens;
			this.outTokens += outTokens;
		}
	}
	
}
