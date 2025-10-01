package de.extio.lmlib.agent.responsehandler;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Completion;

public class TextAgentResponseHandler implements StreamedAgentResponseHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TextAgentResponseHandler.class);
	
	private final String key;
	
	private final String reasoningKey;
	
	private final Function<String, String> transformer;
	
	private final Consumer<AgentContext> beforeStream;
	
	private final Function<Chunk, Chunk> chunkTransformer;
	
	private final BiConsumer<AgentContext, Chunk> afterChunkUpdate;
	
	public TextAgentResponseHandler(final String key) {
		this(key, null, null, null, null);
	}
	
	public TextAgentResponseHandler(final String key, final Function<String, String> transformer, final Consumer<AgentContext> beforeStream, final Function<Chunk, Chunk> chunkTransformer, final BiConsumer<AgentContext, Chunk> afterChunkUpdate) {
		this.key = key;
		this.reasoningKey = key.concat("_reasoning");
		this.transformer = transformer;
		this.beforeStream = beforeStream;
		this.chunkTransformer = chunkTransformer;
		this.afterChunkUpdate = afterChunkUpdate;
	}
	
	@Override
	public boolean handle(final Completion completion, final AgentContext context) {
		var response = completion.response();
		final var reasoning = completion.reasoning();
		if (reasoning != null && !reasoning.isBlank()) {
			context.setStringValue(this.reasoningKey, reasoning);
			LOGGER.debug("Reasoning: {}; Response: {}", reasoning, response);
		}
		else {
			LOGGER.debug("Response: {}", response);
		}
		if (this.transformer != null) {
			response = this.transformer.apply(response);
		}
		context.setStringValue(this.key, response);
		return true;
	}
	
	@Override
	public void beforeStream(final AgentContext context) {
		context.setStringValue(this.key, "");
		context.getContext().remove(this.reasoningKey);
		if (this.beforeStream != null) {
			this.beforeStream.accept(context);
		}
	}
	
	@Override
	public boolean handleChunk(final Chunk chunk_, final AgentContext context) {
		var chunk = chunk_;
		if (this.chunkTransformer != null) {
			chunk = this.chunkTransformer.apply(chunk);
		}
		if (afterChunkUpdate != null) {
			if (chunk.content() != null) {
				context.setStringValue(this.key, Objects.requireNonNullElseGet(context.getStringValue(this.key), () -> "").concat(chunk.content()));
			}
			if (chunk.reasoningContent() != null) {
				context.setStringValue(this.reasoningKey, Objects.requireNonNullElseGet(context.getStringValue(this.reasoningKey), () -> "").concat(chunk.reasoningContent()));
			}
			
			afterChunkUpdate.accept(context, chunk);
		}
		return true;
	}
	
}
