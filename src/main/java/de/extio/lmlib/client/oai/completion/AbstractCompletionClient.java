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
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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

public abstract class AbstractCompletionClient implements Client, DisposableBean {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCompletionClient.class);
	
	@Autowired
	@Qualifier("lmLibRestClientBuilder")
	protected RestClient.Builder restClientBuilder;
	
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
				.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
				.changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))				
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
				.enable(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)
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
	
	protected void configureStreamOptions(final boolean enableStreaming, final Consumer<StreamOptions> streamOptionsConsumer) {
		if (enableStreaming) {
			final var streamOptions = new StreamOptions();
			streamOptions.setIncludeUsage(true);
			streamOptionsConsumer.accept(streamOptions);
		}
	}
	
	protected de.extio.lmlib.client.CompletionStatistics createCompletionStatistics(final ModelProfile modelProfile, final LocalDateTime start, final Usage usage, final Timings timings, final String prompt, final String response, final String reasoning) {
		int inTokens = 0;
		int cachedInTokens = 0;
		int outTokens = 0;
		int reasoningOutTokens = 0;
		
		// Usage data has priority (official OAI spec)
		if (usage != null && usage.getPromptTokens() > 0) {
			cachedInTokens = usage.getCachedTokens();
			inTokens = usage.getPromptTokens() - cachedInTokens;
			reasoningOutTokens = usage.getReasoningTokens();
			outTokens = usage.getCompletionTokens() - reasoningOutTokens;
		}
		// If no usage data, try to get token counts from timings (e.g. llama.cpp, prompt_n does not include cache_n)
		if (timings != null) {
			if (inTokens <= 0 && cachedInTokens <= 0 && (timings.getPromptN() > 0 || timings.getCacheN() > 0)) {
				inTokens = Math.max(0, timings.getPromptN());
				cachedInTokens = Math.max(0, timings.getCacheN());
			}
			else if (cachedInTokens <= 0 && timings.getCacheN() > 0) {
				cachedInTokens = Math.max(0, timings.getCacheN());
				inTokens -= cachedInTokens;
			}
			if (outTokens <= 0 && timings.getPredictedN() > 0) {
				outTokens = timings.getPredictedN();
			}
		}
		// Fallback to tokenizer estimation
		if (inTokens <= 0 && prompt != null && !prompt.isBlank()) {
			inTokens = this.tokenizer.count(prompt, modelProfile);
		}
		if (reasoningOutTokens <= 0 && reasoning != null && !reasoning.isBlank()) {
			reasoningOutTokens = this.tokenizer.count(reasoning, modelProfile);
			outTokens -= reasoningOutTokens;
		}
		if (outTokens <= 0 && response != null && !response.isBlank()) {
			outTokens = this.tokenizer.count(response, modelProfile);
		}
		
		// Calculate cost - inTokens and outTokens are already exclusive of cached/reasoning tokens
		BigDecimal cost = BigDecimal.ZERO;
		cost = cost.add(new BigDecimal(inTokens).multiply(modelProfile.costPerInToken()));
		cost = cost.add(new BigDecimal(cachedInTokens).multiply(modelProfile.costPerCachedInToken()));
		cost = cost.add(new BigDecimal(outTokens).multiply(modelProfile.costPerOutToken()));
		cost = cost.add(new BigDecimal(reasoningOutTokens).multiply(modelProfile.costPerReasoningOutToken()));
		
		final var statistics = new de.extio.lmlib.client.CompletionStatistics(
				1,
				Duration.between(start, LocalDateTime.now()),
				inTokens,
				cachedInTokens,
				outTokens,
				reasoningOutTokens,
				cost,
				false);
		LOGGER.debug("{}", statistics);
		if (this.collectStatistics) {
			this.totalStatistics.add(statistics.duration(), inTokens, cachedInTokens, outTokens, reasoningOutTokens, statistics.cost());
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
		
		private long cachedInTokens;
		
		private long outTokens;
		
		private long reasoningOutTokens;
		
		private BigDecimal cost = BigDecimal.ZERO;
		
		synchronized void add(final Duration duration, final int inTokens, final int cachedInTokens, final int outTokens, final int reasoningOutTokens, final BigDecimal cost) {
			this.requests++;
			this.duration = this.duration.plus(duration);
			this.inTokens += inTokens;
			this.cachedInTokens += cachedInTokens;
			this.outTokens += outTokens;
			this.reasoningOutTokens += reasoningOutTokens;
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
			builder.append(", cachedInTokens=");
			builder.append(this.cachedInTokens);
			builder.append(", outTokens=");
			builder.append(this.outTokens);
			builder.append(", reasoningOutTokens=");
			builder.append(this.reasoningOutTokens);
			builder.append(", averageTps=");
			final long totalOutTokens = this.outTokens + this.reasoningOutTokens;
			builder.append(new DecimalFormat("0.##").format(((double) (totalOutTokens + inTokens) / (double) this.requests) / ((double) this.duration.toMillis() / 1000.0 / (double) this.requests)));
			builder.append(", averageOutTps=");
			builder.append(new DecimalFormat("0.##").format((double) totalOutTokens / ((double) this.duration.toMillis() / 1000.0)));
			builder.append(", effectiveDuration=");
			final var effectiveDuration = Duration.between(this.start, Instant.now());
			builder.append(effectiveDuration);
			builder.append(", effectiveTps=");
			builder.append(new DecimalFormat("0.##").format(((double) (totalOutTokens + inTokens) / (double) this.requests) / ((double) effectiveDuration.toMillis() / 1000.0 / (double) this.requests)));
			builder.append(", effectiveOutTps=");
			builder.append(new DecimalFormat("0.##").format((double) totalOutTokens / ((double) effectiveDuration.toMillis() / 1000.0)));
			builder.append(", cost=");
			builder.append(new DecimalFormat("0.###").format(cost.doubleValue()));
			builder.append("]");
			return builder.toString();
		}
		
	}
	
}
