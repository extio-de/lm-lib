package de.extio.lmlib.client;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Captures the arrival time of the first streamed content or reasoning chunk.
 * Clients use it to separate prefill time from generation time.
 */
public final class StreamingTiming {

	public static StreamingTiming create() {
		return new StreamingTiming();
	}

	private final AtomicReference<LocalDateTime> firstToken = new AtomicReference<>();

	private StreamingTiming() {
	}

	/**
	 * Wraps a chunk consumer and records the time of the first delivered chunk.
	 *
	 * @param chunkConsumer the consumer to wrap, may be null for non-streaming requests
	 * @return the wrapped consumer, null when the given consumer is null
	 */
	public Consumer<Chunk> wrap(final Consumer<Chunk> chunkConsumer) {
		if (chunkConsumer == null) {
			return null;
		}
		return chunk -> {
			this.firstToken.compareAndSet(null, LocalDateTime.now());
			chunkConsumer.accept(chunk);
		};
	}

	/**
	 * Returns the time of the first streamed chunk, null when no chunk arrived.
	 */
	public LocalDateTime getFirstToken() {
		return this.firstToken.get();
	}

}
