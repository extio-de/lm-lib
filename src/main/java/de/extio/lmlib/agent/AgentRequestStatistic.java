package de.extio.lmlib.agent;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.CompletionStatistics;

public final class AgentRequestStatistic {
	
	public static AgentRequestStatistic create() {
		return new AgentRequestStatistic(true);
	}
	
	public static AgentRequestStatistic createWithoutEffectiveDuration() {
		return new AgentRequestStatistic(false);
	}
	
	private AgentRequestStatistic(final boolean withEffectiveDuration) {
		if (withEffectiveDuration) {
			this.start = Instant.now();
		}
	}
	
	private final AtomicInteger requests = new AtomicInteger();
	
	private final AtomicInteger cachedPrompts = new AtomicInteger();
	
	private final AtomicLong inTokens = new AtomicLong();
	
	private final AtomicLong cachedInTokens = new AtomicLong();
	
	private final AtomicLong outTokens = new AtomicLong();
	
	private final AtomicLong reasoningOutTokens = new AtomicLong();
	
	/** Accumulated tokens and phase durations to average the reported throughputs over. */
	private final AtomicLong prefillTpsTokens = new AtomicLong();
	
	private final AtomicLong prefillTpsMillis = new AtomicLong();
	
	private final AtomicLong outTpsTokens = new AtomicLong();
	
	private final AtomicLong outTpsMillis = new AtomicLong();
	
	private final AtomicLong requestTpsTokens = new AtomicLong();
	
	private final AtomicLong requestTpsMillis = new AtomicLong();
	
	private volatile Instant start;
	
	private volatile Duration requestDuration = Duration.ofMillis(0L);
	
	private volatile BigDecimal cost = BigDecimal.ZERO;
	
	public AtomicInteger getRequests() {
		return this.requests;
	}
	
	public AtomicInteger getCachedPrompts() {
		return this.cachedPrompts;
	}
	
	public AtomicLong getInTokens() {
		return this.inTokens;
	}
	
	public AtomicLong getCachedInTokens() {
		return this.cachedInTokens;
	}
	
	public AtomicLong getOutTokens() {
		return this.outTokens;
	}
	
	public AtomicLong getReasoningOutTokens() {
		return this.reasoningOutTokens;
	}
	
	public Duration getRequestDuration() {
		return this.requestDuration;
	}
	
	public Duration getEffectiveDuration() {
		if (this.start == null) {
			return null;
		}
		return Duration.between(this.start, Instant.now());
	}
	
	/**
	 * Returns the averaged input tokens per second spent on prefill. The phase durations are
	 * reconstructed from the reported throughputs, so faster and slower requests are weighted by
	 * their tokens instead of their count.
	 */
	public int getPrefillTps() {
		return CompletionStatistics.tokensPerSecond(this.prefillTpsTokens.get(), Duration.ofMillis(this.prefillTpsMillis.get()));
	}
	
	/**
	 * Returns the averaged output and reasoning tokens per second spent on generation.
	 */
	public int getOutTps() {
		return CompletionStatistics.tokensPerSecond(this.outTpsTokens.get(), Duration.ofMillis(this.outTpsMillis.get()));
	}
	
	/**
	 * Returns the averaged output and reasoning tokens per second over the request durations.
	 */
	public int getRequestTps() {
		return CompletionStatistics.tokensPerSecond(this.requestTpsTokens.get(), Duration.ofMillis(this.requestTpsMillis.get()));
	}
	
	public BigDecimal getCost() {
		return this.cost;
	}
	
	public void add(final Completion completion) {
		if (completion.statistics().cached()) {
			this.cachedPrompts.incrementAndGet();
		}
		else {
			this.getRequests().addAndGet(completion.statistics().requests());
			this.getInTokens().addAndGet(completion.statistics().inTokens());
			this.getCachedInTokens().addAndGet(completion.statistics().cachedInTokens());
			this.getOutTokens().addAndGet(completion.statistics().outTokens());
			this.getReasoningOutTokens().addAndGet(completion.statistics().reasoningOutTokens());
			this.addThroughput(completion.statistics());
		}
		synchronized (this) {
			this.requestDuration = this.requestDuration.plus(completion.statistics().duration());
			this.cost = this.cost.add(completion.statistics().cost());
		}
	}
	
	/**
	 * Collects a throughput sample. A missing throughput is not measured and therefore not weighted.
	 */
	private void addThroughput(final CompletionStatistics statistics) {
		addSample(this.prefillTpsTokens, this.prefillTpsMillis, statistics.prefillTps(), statistics.inTokens() + statistics.cachedInTokens());
		final var outTokens = statistics.outTokens() + statistics.reasoningOutTokens();
		addSample(this.outTpsTokens, this.outTpsMillis, statistics.outTps(), outTokens);
		addSample(this.requestTpsTokens, this.requestTpsMillis, statistics.requestTps(), outTokens);
	}
	
	private static void addSample(final AtomicLong tokens, final AtomicLong millis, final int tokensPerSecond, final long tokenCount) {
		if (tokensPerSecond <= 0 || tokenCount <= 0) {
			return;
		}
		tokens.addAndGet(tokenCount);
		millis.addAndGet(Math.max(1L, Math.round((double) tokenCount * 1_000.0 / (double) tokensPerSecond)));
	}
	
	public void add(final AgentRequestStatistic other) {
		this.getRequests().addAndGet(other.getRequests().get());
		this.getCachedPrompts().addAndGet(other.getCachedPrompts().get());
		this.getInTokens().addAndGet(other.getInTokens().get());
		this.getCachedInTokens().addAndGet(other.getCachedInTokens().get());
		this.getOutTokens().addAndGet(other.getOutTokens().get());
		this.getReasoningOutTokens().addAndGet(other.getReasoningOutTokens().get());
		this.prefillTpsTokens.addAndGet(other.prefillTpsTokens.get());
		this.prefillTpsMillis.addAndGet(other.prefillTpsMillis.get());
		this.outTpsTokens.addAndGet(other.outTpsTokens.get());
		this.outTpsMillis.addAndGet(other.outTpsMillis.get());
		this.requestTpsTokens.addAndGet(other.requestTpsTokens.get());
		this.requestTpsMillis.addAndGet(other.requestTpsMillis.get());
		synchronized (this) {
			if (this.start != null && other.start.isBefore(this.start)) {
				this.start = other.start;
			}
			this.requestDuration = this.requestDuration.plus(other.getRequestDuration());
			this.cost = this.cost.add(other.getCost());
		}
	}
	
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("AgentRequestStatistic [requests=");
		builder.append(this.requests);
		builder.append(", cachedPrompts=");
		builder.append(this.cachedPrompts);
		builder.append(", inTokens=");
		builder.append(this.inTokens);
		builder.append(", cachedInTokens=");
		builder.append(this.cachedInTokens);
		builder.append(", outTokens=");
		builder.append(this.outTokens);
		builder.append(", reasoningOutTokens=");
		builder.append(this.reasoningOutTokens);
		builder.append(", requestDuration=");
		builder.append(this.requestDuration);
		builder.append(", prefillTps=");
		builder.append(this.getPrefillTps());
		builder.append(", outTps=");
		builder.append(this.getOutTps());
		builder.append(", requestTps=");
		builder.append(this.getRequestTps());
		builder.append(", getEffectiveDuration()=");
		builder.append(this.getEffectiveDuration());
		builder.append(", getCost()=");
		builder.append(this.getCost());
		builder.append("]");
		return builder.toString();
	}
	
}
