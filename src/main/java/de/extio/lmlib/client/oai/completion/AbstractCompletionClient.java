package de.extio.lmlib.client.oai.completion;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.oai.ModelNameSupplier;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;
import de.extio.lmlib.profile.ModelProfileService;
import de.extio.lmlib.token.Tokenizer;
import reactor.util.retry.Retry;

public abstract class AbstractCompletionClient implements Client, DisposableBean {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCompletionClient.class);
	
	@Autowired
	@Qualifier("lmLibWebClientBuilder")
	protected WebClient.Builder webClientBuilder;
	
	@Autowired
	protected ModelNameSupplier modelNameSupplier;
	
	@Autowired
	protected Tokenizer tokenizer;
	
	@Autowired
	protected ModelProfileService modelProfileService;
	
	@Value("${client.collectStatistics:false}")
	protected boolean collectStatistics;
	
	protected final CompletionStatistics totalStatistics = new CompletionStatistics();
	
	protected final ObjectMapper objectMapper;
	
	public AbstractCompletionClient() {
		this.objectMapper = JsonMapper.builder()
				.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
				.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
				.configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, false)
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false)
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
				.enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
				.enable(JsonReadFeature.ALLOW_MISSING_VALUES)
				.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
				.enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
				.enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
				.enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
				.enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
				.enable(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
				.enable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
				.enable(JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)
				.enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
				.build();
	}
	
	@Override
	public Completion completion(final ModelCategory modelCategory, final String system, final String text) {
		return this.conversation(modelCategory, Conversation.create(system, text));
	}
	
	@Override
	public Completion conversation(final ModelCategory modelCategory, final Conversation conversation) {
		return this.streamConversation(modelCategory, conversation, null);
	}
	
	@Override
	public Completion conversation(final ModelProfile modelProfile, final Conversation conversation) {
		return this.streamConversation(modelProfile, conversation, null);
	}
	
	@Override
	public Completion streamConversation(final ModelCategory modelCategory_, final Conversation conversation, final Consumer<Chunk> chunkConsumer) {
		final ModelCategory modelCategory = (modelCategory_ == null) ? ModelCategory.MEDIUM : modelCategory_;
		final var modelProfile = this.modelProfileService.getModelProfile(modelCategory.getModelProfile(), modelCategory);
		return this.streamConversation(modelProfile, conversation, chunkConsumer);
	}
	
	@Override
	public Completion streamConversation(final ModelProfile modelProfile, final Conversation conversation, final Consumer<Chunk> chunkConsumer) {
		if (modelProfile == null || (modelProfile.modelProvider() != ModelProvider.OAI_TEXT_COMPLETION && modelProfile.modelProvider() != ModelProvider.OAI_CHAT_COMPLETION)) {
			throw new IllegalArgumentException("Invalid ModelProfile");
		}
		
		return this.requestCompletion(conversation, modelProfile, chunkConsumer);
	}
	
	protected abstract Completion requestCompletion(final Conversation conversation, final ModelProfile modelProfile, final Consumer<Chunk> chunkConsumer);
	
	protected CompletionFinishReason mapFinishReason(final String finishReason) {
		return switch (finishReason) {
			case FinishReasons.FINISH_REASON_CONTENT_FILTER -> CompletionFinishReason.CONTENT_FILTERED;
			case FinishReasons.FINISH_REASON_LENGTH -> CompletionFinishReason.TOKEN_LIMIT_REACHED;
			default -> CompletionFinishReason.DONE;
		};
	}
	
	protected Retry createRetrySpec() {
		return Retry.backoff(99, Duration.ofSeconds(1))
				.maxBackoff(Duration.ofSeconds(10))
				.jitter(0.25d)
				.filter(throwable -> {
					return throwable instanceof java.io.IOException || throwable instanceof java.net.ConnectException || throwable instanceof java.net.http.HttpTimeoutException;
				})
				.doAfterRetry(rs -> LOGGER.warn("Retrying failed request: " + rs.failure().getClass().getName() + " " + rs.failure().getMessage()));
	}
	
	protected void configureStreamOptions(final boolean enableStreaming, final Consumer<StreamOptions> streamOptionsConsumer) {
		if (enableStreaming) {
			final var streamOptions = new StreamOptions();
			streamOptions.setIncludeUsage(true);
			streamOptionsConsumer.accept(streamOptions);
		}
	}
	
	protected de.extio.lmlib.client.CompletionStatistics createCompletionStatistics(final ModelProfile modelProfile, final LocalDateTime start, final Usage usage, final String prompt, final String response) {
		int inTokens = 0;
		int outTokens = 0;
		if (usage != null && usage.getPromptTokens() > 0) {
			inTokens = usage.getPromptTokens();
			outTokens = usage.getCompletionTokens();
		}
		else {
			if (prompt != null) {
				inTokens = this.tokenizer.count(prompt, modelProfile);
			}
			if (response != null) {
				outTokens = this.tokenizer.count(response, modelProfile);
			}
		}
		
		final var statistics = new de.extio.lmlib.client.CompletionStatistics(
				1,
				Duration.between(start, LocalDateTime.now()),
				inTokens,
				outTokens,
				new BigDecimal(inTokens).multiply(modelProfile.costPerInToken()).add(new BigDecimal(outTokens).multiply(modelProfile.costPerOutToken())),
				false);
		LOGGER.debug("{}", statistics);
		if (this.collectStatistics) {
			this.totalStatistics.add(statistics.duration(), inTokens, outTokens, statistics.cost());
		}
		return statistics;
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
