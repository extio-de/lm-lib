package de.extio.lmlib.client.oai.textcompletion;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

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
		
		final List<StringBuilder> prompts = this.createPrompts(conversation, modelCategory, modelProfile, promptStrategy);
		
		final List<StringBuilder> answers = new ArrayList<>();
		final CompletionStatistics statistics = new CompletionStatistics();
		for (final StringBuilder prompt : prompts) {
			final StringBuilder answer = new StringBuilder();
			this.requestCompletionsContinuations(prompt, answer, statistics, modelProfile, promptStrategy);
			answers.add(answer);
		}
		
		if (answers.size() > 1) {
			this.summarize(answers, modelProfile, promptStrategy, statistics);
		}
		
		return new Completion(
				answers.stream().collect(Collectors.joining()),
				statistics.requests,
				statistics.duration,
				statistics.inTokens,
				statistics.outTokens,
				new BigDecimal(statistics.inTokens).multiply(modelProfile.costInToken()).add(new BigDecimal(statistics.outTokens).multiply(modelProfile.costOutToken())),
				false);
	}
	
	private List<StringBuilder> createPrompts(final Conversation conversation, final ModelCategory modelCategory, final ModelProfile modelProfile, final PromptStrategy promptStrategy) {
		StringBuilder prompt = this.formatConversation(conversation, promptStrategy);
		
		List<Long> tokenized = this.tokenizer.tokenize(prompt.toString(), modelProfile);
		final int maxLength = modelProfile.maxContextLength() - modelProfile.maxTokens() - 15; // 15 is a margin for EOT and other special tokens
		if (tokenized.size() > maxLength) {
			// TODO: Cut smarter, e.g. split conversation into parts while keeping system prompt
			LOGGER.warn("Prompt too long: {} tokens / {} max. Prompt will be cut!", tokenized.size(), maxLength);
			tokenized = tokenized.subList(0, maxLength);
			prompt = new StringBuilder(this.tokenizer.detokenize(tokenized, modelProfile));
		}
		
		LOGGER.info("Completion request for {}. Input tokens: {}", modelCategory, tokenized.size());
		
		return List.of(prompt);
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
	
	private void requestCompletionsContinuations(final StringBuilder prompt, final StringBuilder answer, final CompletionStatistics statistics,
			final ModelProfile modelProfile, final PromptStrategy promptStrategy) {
		for (int continuation = 0; continuation < modelProfile.maxContinuations(); continuation++) {
			final LocalDateTime start = LocalDateTime.now();
			
			final String promptStr = prompt.toString();
			
			final var response = this.requestCompletions(promptStr, modelProfile);
			
			String content = null;
			var complete = false;
			if (response.getContent() != null) {
				content = promptStrategy.removeEOT(response.getContent());
				complete = response.isStoppedEos();
			}
			else {
				final var choice = response.getChoices().getFirst();
				content = promptStrategy.removeEOT(choice.getText());
				complete = !Choice.FINISH_REASON_LENGTH.equals(choice.getFinishReason());
			}
			
			if (this.collectStatistics) {
				final var dur = Duration.between(start, LocalDateTime.now());
				final var in = this.tokenizer.count(promptStr, modelProfile);
				final var out = this.tokenizer.count(content, modelProfile);
				final var cost = new BigDecimal(in).multiply(modelProfile.costInToken()).add(new BigDecimal(out).multiply(modelProfile.costOutToken()));
				LOGGER.debug("Request duration: " + dur + "; in tokens: " + in + "; out tokens: " + out + "; cost: " + new DecimalFormat("0.####").format(cost));
				statistics.add(dur, in, out, cost);
				this.totalStatistics.add(dur, in, out, cost);
			}
			
			answer.append(content);
			
			if (complete) {
				LOGGER.debug("Prompt response complete");
				break;
			}
			
			answer.append('\n');
			promptStrategy.continue_(prompt, content);
		}
	}
	
	private CompletionResponse requestCompletions(final String prompt, final ModelProfile modelProfile) {
		LOGGER.debug("Requesting completion at {}", modelProfile.url());
		
		final var request = new CompletionRequest();
		request.setModel(this.modelNameSupplier.getModelName(modelProfile.url()));
		request.setPrompt(prompt);
		request.setMaxTokens(modelProfile.maxTokens());
		request.setTemperature(modelProfile.temperature());
		request.setTopP(modelProfile.topP());
		request.setStream(false);
		
		final var webClient = this.webClientBuilder.baseUrl(modelProfile.url()).build();
		return webClient
				.method(HttpMethod.POST)
				.uri(uriBuilder -> uriBuilder.path("/v1/completions").build())
				.bodyValue(request)
				.retrieve()
				.bodyToMono(CompletionResponse.class)
				.retryWhen(Retry.backoff(99, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(10)).jitter(0.25d).doAfterRetry(rs -> LOGGER.warn("Request failed " + rs.failure().getMessage())))
				.block();
	}
	
	private void summarize(final List<StringBuilder> answers, final ModelProfile modelProfile, final PromptStrategy promptStrategy, final CompletionStatistics statistics) {
		// TODO: Reactivate when multiple (split) prompts have been implemented again in #createPrompts
		//		LOGGER.debug("Summarizing answers");
		//		
		//		final StringBuilder summary = new StringBuilder();
		//		
		//		final StringBuilder prompt = promptStrategy.start(instruction, question, "");
		//		answers.forEach(answer -> promptStrategy.next(prompt, answer.toString(), "Continue"));
		//		promptStrategy.next(prompt, "", "Generate now a full summary");
		//		
		//		this.requestCompletionsContinuations(prompt, summary, statistics, modelProfile, promptStrategy);
		//		
		//		answers.clear();
		//		answers.add(summary);
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
