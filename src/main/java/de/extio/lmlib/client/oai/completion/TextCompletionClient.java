package de.extio.lmlib.client.oai.completion;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.oai.ModelNameSupplier;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;
import de.extio.lmlib.profile.ModelProfileService;
import de.extio.lmlib.prompt.PromptStrategy;
import de.extio.lmlib.prompt.PromptStrategyFactory;
import de.extio.lmlib.token.Tokenizer;
import reactor.util.retry.Retry;

@Component
public class TextCompletionClient implements Client, DisposableBean {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TextCompletionClient.class);
	
	@Autowired
	@Qualifier("lmLibWebClientBuilder")
	private WebClient.Builder webClientBuilder;
	
	@Autowired
	private PromptStrategyFactory promptStrategyFactory;
	
	@Autowired
	private ModelNameSupplier modelNameSupplier;
	
	@Autowired
	private Tokenizer tokenizer;
	
	@Autowired
	private ModelProfileService modelProfileService;
	
	@Value("${client.collectStatistics:false}")
	private boolean collectStatistics;
	
	private final CompletionStatistics totalStatistics = new CompletionStatistics();
	
	@Override
	public ModelProvider getModelProvider() {
		return ModelProvider.OAI_TEXT_COMPLETION;
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
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.OAI_TEXT_COMPLETION) {
			throw new IllegalArgumentException("ModelProfile " + modelCategory.getModelProfile());
		}
		
		final PromptStrategy promptStrategy = this.promptStrategyFactory.getStrategy(modelProfile.prompt());
		if (promptStrategy == null) {
			throw new IllegalArgumentException("Prompt strategy not found: " + modelProfile.prompt());
		}
		
		final var prompt = this.createPrompt(conversation, modelCategory, modelProfile, promptStrategy);
		
		return this.requestCompletion(prompt, modelProfile, promptStrategy);
	}
	
	private String createPrompt(final Conversation conversation, final ModelCategory modelCategory, final ModelProfile modelProfile, final PromptStrategy promptStrategy) {
		StringBuilder prompt = this.formatConversation(conversation, promptStrategy);
		
		List<Long> tokenized = this.tokenizer.tokenize(prompt.toString(), modelProfile);
		final int maxLength = modelProfile.maxContextLength() - modelProfile.maxTokens() - 15; // 15 is a margin for EOT and other special tokens
		if (tokenized.size() > maxLength) {
			LOGGER.warn("Prompt too long: {} tokens / {} max. Prompt will be cut!", tokenized.size(), maxLength);
			tokenized = tokenized.subList(0, maxLength);
			prompt = new StringBuilder(this.tokenizer.detokenize(tokenized, modelProfile));
		}
		
		LOGGER.info("Completion request for {}. Input tokens: {}", modelCategory, tokenized.size());
		
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
	
	private Completion requestCompletion(final String prompt, final ModelProfile modelProfile, final PromptStrategy promptStrategy) {
		LOGGER.debug("Requesting completion at {}", modelProfile.url());
		final LocalDateTime start = LocalDateTime.now();
		
		final var request = new CompletionRequest();
		request.setModel(this.modelNameSupplier.getModelName(modelProfile.url()));
		request.setPrompt(prompt);
		request.setMaxTokens(modelProfile.maxTokens());
		request.setTemperature(modelProfile.temperature());
		request.setTopP(modelProfile.topP());
		request.setStream(false);
		
		final var webClient = this.webClientBuilder.baseUrl(modelProfile.url()).build();
		final var response = webClient
				.method(HttpMethod.POST)
				.uri(uriBuilder -> uriBuilder.path("/v1/completions").build())
				.header("Content-Type", "application/json")
				.bodyValue(request)
				.retrieve()
				.bodyToMono(CompletionResponse.class)
				.retryWhen(Retry.backoff(99, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(10)).jitter(0.25d).doAfterRetry(rs -> LOGGER.warn("Request failed " + rs.failure().getMessage())))
				.block();
		
		String content;
		CompletionFinishReason finishReason;
		if (response.getChoices() != null && !response.getChoices().isEmpty()) {
			final var choice = response.getChoices().getFirst();
			content = promptStrategy.removeEOT(choice.getText());
			finishReason = switch (choice.getFinishReason()) {
				case Choice.FINISH_REASON_CONTENT_FILTER -> CompletionFinishReason.CONTENT_FILTERED;
				case Choice.FINISH_REASON_LENGTH -> CompletionFinishReason.TOKEN_LIMIT_REACHED;
				default -> CompletionFinishReason.DONE;
			};
		}
		else if (response.getContent() != null) {
			content = promptStrategy.removeEOT(response.getContent());
			finishReason = response.isStoppedEos() ? CompletionFinishReason.DONE : CompletionFinishReason.TOKEN_LIMIT_REACHED;
		}
		else {
			throw new IllegalStateException("No parseable response content from server");
		}
		
		int inTokens;
		int outTokens;
		if (response.getUsage() != null && response.getUsage().getPromptTokens() > 0) {
			inTokens = response.getUsage().getPromptTokens();
			outTokens = response.getUsage().getCompletionTokens();
		}
		else {
			inTokens = this.tokenizer.count(prompt, modelProfile);
			outTokens = this.tokenizer.count(content, modelProfile);
		}
		
		final var statistics = new de.extio.lmlib.client.CompletionStatistics(
				1,
				Duration.between(start, LocalDateTime.now()),
				inTokens,
				outTokens,
				new BigDecimal(inTokens).multiply(modelProfile.costInToken()).add(new BigDecimal(outTokens).multiply(modelProfile.costOutToken())),
				false);
		LOGGER.debug("{}", statistics);
		if (this.collectStatistics) {
			this.totalStatistics.add(statistics.duration(), inTokens, outTokens, statistics.cost());
		}
		
		return new Completion(content, finishReason, statistics);
	}
	
	@Override
	public void destroy() throws Exception {
		if (this.collectStatistics && this.totalStatistics.requests > 0) {
			LOGGER.info("Client Statistics: {}", this.totalStatistics);
		}
	}
	
	static class CompletionStatistics {
		
		private final Instant start = Instant.now();
		
		private int requests;
		
		private Duration duration = Duration.ofMillis(0l);
		
		private long inTokens;
		
		private long outTokens;
		
		private BigDecimal cost = BigDecimal.ZERO;
		
		synchronized void add(final Duration duration, final int inTokens, final int outTokens, final BigDecimal cost) {
			this.requests++;
			this.duration = this.duration.plus(duration);
			this.inTokens += inTokens;
			this.outTokens += outTokens;
			this.cost = this.cost.add(cost);
		}
		
		@Override
		public String toString() {
			final StringBuilder builder = new StringBuilder();
			builder.append("CompletionStatistics [requests=");
			builder.append(this.requests);
			builder.append(", duration=");
			builder.append(this.duration);
			builder.append(", inTokens=");
			builder.append(this.inTokens);
			builder.append(", outTokens=");
			builder.append(this.outTokens);
			builder.append(", averageTps=");
			builder.append(new DecimalFormat("0.##").format(((double) (this.outTokens + this.inTokens) / (double) this.requests) / ((double) this.duration.toMillis() / 1000.0 / (double) this.requests)));
			builder.append(", averageOutTps=");
			builder.append(new DecimalFormat("0.##").format((double) this.outTokens / ((double) this.duration.toMillis() / 1000.0)));
			builder.append(", effectiveDuration=");
			final var effectiveDuration = Duration.between(this.start, Instant.now());
			builder.append(effectiveDuration);
			builder.append(", effectiveTps=");
			builder.append(new DecimalFormat("0.##").format(((double) (this.outTokens + this.inTokens) / (double) this.requests) / ((double) effectiveDuration.toMillis() / 1000.0 / (double) this.requests)));
			builder.append(", effectiveOutTps=");
			builder.append(new DecimalFormat("0.##").format((double) this.outTokens / ((double) effectiveDuration.toMillis() / 1000.0)));
			builder.append(", cost=");
			builder.append(new DecimalFormat("0.###").format(cost.doubleValue()));
			builder.append("]");
			return builder.toString();
		}
		
	}
	
}
