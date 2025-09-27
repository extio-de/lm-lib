package de.extio.lmlib.agent;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import de.extio.lmlib.client.Completion;

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
	
	private final AtomicLong outTokens = new AtomicLong();
	
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
	
	public AtomicLong getOutTokens() {
		return this.outTokens;
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
	
	public String getTps() {
		final var duration = this.start == null ? this.getRequestDuration() : this.getEffectiveDuration();
		return new DecimalFormat("#.##").format((double) (this.inTokens.get() + this.outTokens.get()) / (double) duration.toMillis() * 1000.0);
	}
	
	public String getOutTps() {
		final var duration = this.start == null ? this.getRequestDuration() : this.getEffectiveDuration();
		return new DecimalFormat("#.##").format((double) this.outTokens.get() / (double) duration.toMillis() * 1000.0);
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
		}
		this.getInTokens().addAndGet(completion.statistics().inTokens());
		this.getOutTokens().addAndGet(completion.statistics().outTokens());
		synchronized (this) {
			this.requestDuration = this.requestDuration.plus(completion.statistics().duration());
			this.cost = this.cost.add(completion.statistics().cost());
		}
	}
	
	public void add(final AgentRequestStatistic other) {
		this.getRequests().addAndGet(other.getRequests().get());
		this.getCachedPrompts().addAndGet(other.getCachedPrompts().get());
		this.getInTokens().addAndGet(other.getInTokens().get());
		this.getOutTokens().addAndGet(other.getOutTokens().get());
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
		builder.append(", outTokens=");
		builder.append(this.outTokens);
		builder.append(", requestDuration=");
		builder.append(this.requestDuration);
		builder.append(", getEffectiveDuration()=");
		if (this.start != null) {
			builder.append(this.getEffectiveDuration());
			builder.append(", getTps()=");
		}
		builder.append(this.getTps());
		builder.append(", getOutTps()=");
		builder.append(this.getOutTps());
		builder.append(", getCost()=");
		builder.append(this.getCost());
		builder.append("]");
		return builder.toString();
	}
	
}
