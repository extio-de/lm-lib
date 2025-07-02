package de.extio.lmlib.agent.responsehandler;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Completion;

public class TextAgentResponseHandler implements StreamedAgentResponseHandler {
	
	public static final String UPDATE_KEY = "chunkUpdateKey";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TextAgentResponseHandler.class);

	private final String key;
	
	private final String chunkKey;
	
	private final Function<String, String> transformer;
	
	private final Consumer<AgentContext> beforeStream;

	private final Function<String, String> chunkTransformer;

	private final Consumer<AgentContext> afterChunkUpdate;
	
	public TextAgentResponseHandler(final String key) {
		this(key, null, null, null, null);
	}
	
	public TextAgentResponseHandler(final String key, final Function<String, String> transformer, final Consumer<AgentContext> beforeStream, final Function<String, String> chunkTransformer, final Consumer<AgentContext> afterChunkUpdate) {
		this.key = key;
		this.chunkKey = key.concat("_chunk");
		this.transformer = transformer;
		this.beforeStream = beforeStream;
		this.chunkTransformer = chunkTransformer;
		this.afterChunkUpdate = afterChunkUpdate;
	}
	
	@Override
	public boolean handle(final Completion completion, final AgentContext context) {
		var response = completion.response();
		LOGGER.debug("Response: {}", response);
		if (this.transformer != null) {
			response = this.transformer.apply(response);
		}
		context.setStringValue(this.key, response);
		return true;
	}
	
	@Override
	public void beforeStream(final AgentContext context) {
		context.setStringValue(this.key, "");
		if (this.beforeStream != null) {
			this.beforeStream.accept(context);
		}
	}
	
	@Override
	public boolean handleChunk(final String chunk_, final AgentContext context) {
		var chunk = chunk_;
		if (this.chunkTransformer != null) {
			chunk = this.chunkTransformer.apply(chunk);
		}
		context.setStringValue(UPDATE_KEY, this.key);
		if (afterChunkUpdate != null) {
			context.setStringValue(this.chunkKey, chunk);
			context.setStringValue(this.key, Objects.requireNonNullElse(context.getStringValue(this.key), "").concat(chunk));
			afterChunkUpdate.accept(context);
			context.getContext().remove(UPDATE_KEY);
			context.getContext().remove(this.chunkKey);
		}
		return true;
	}
	
}
