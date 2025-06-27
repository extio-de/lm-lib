package de.extio.lmlib.client.oai.completion;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;

import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
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
		if (modelProfile == null || (modelProfile.modelProvider() != ModelProvider.OAI_TEXT_COMPLETION && modelProfile.modelProvider() != ModelProvider.OAI_CHAT_COMPLETION)) {
			throw new IllegalArgumentException("ModelProfile " + modelCategory.getModelProfile());
		}
		
		return this.requestCompletion(conversation, modelCategory, modelProfile);
	}
	
	protected abstract Completion requestCompletion(final Conversation conversation, final ModelCategory modelCategory, final ModelProfile modelProfile);
	
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
				new BigDecimal(inTokens).multiply(modelProfile.costInToken()).add(new BigDecimal(outTokens).multiply(modelProfile.costOutToken())),
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
