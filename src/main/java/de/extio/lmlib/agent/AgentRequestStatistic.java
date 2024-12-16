package de.extio.lmlib.agent;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import de.extio.lmlib.client.Completion;

public final class AgentRequestStatistic {
	
	private final AtomicInteger requests = new AtomicInteger();
	
	private final AtomicLong inTokens = new AtomicLong();
	
	private final AtomicLong outTokens = new AtomicLong();
	
	private final Instant start = Instant.now();
	
	private volatile Duration requestDuration = Duration.ofMillis(0L);
	
	public AtomicInteger getRequests() {
		return this.requests;
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
	
	public Duration getAbsoluteDuration() {
		return Duration.between(this.start, Instant.now());
	}
	
	public String getTps() {
		return new DecimalFormat("#.##").format((double) (this.inTokens.get() + this.outTokens.get()) / (double)this.getAbsoluteDuration().toMillis() * 1000.0);
	}
	
	public String getOutTps() {
		return new DecimalFormat("#.##").format((double) this.outTokens.get() / (double)this.getAbsoluteDuration().toMillis() * 1000.0);
	}
	
	public void add(final Completion completion) {
		this.getRequests().addAndGet(completion.requests());
		this.getInTokens().addAndGet(completion.inTokens());
		this.getOutTokens().addAndGet(completion.outTokens());
		this.addRequestDuration(completion.duration());
	}
	
	public synchronized void addRequestDuration(final Duration duration) {
		this.requestDuration = this.requestDuration.plus(duration);
	}
	
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("AgentRequestStatistic [requests=");
		builder.append(this.requests);
		builder.append(", inTokens=");
		builder.append(this.inTokens);
		builder.append(", outTokens=");
		builder.append(this.outTokens);
		builder.append(", requestDuration=");
		builder.append(this.requestDuration);
		builder.append(", getAbsoluteDuration()=");
		builder.append(this.getAbsoluteDuration());
		builder.append(", getTps()=");
		builder.append(this.getTps());
		builder.append(", getOutTps()=");
		builder.append(this.getOutTps());
		builder.append("]");
		return builder.toString();
	}
	
}
