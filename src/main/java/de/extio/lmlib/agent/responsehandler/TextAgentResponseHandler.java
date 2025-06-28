package de.extio.lmlib.agent.responsehandler;

import java.util.Objects;
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
	
	private final Function<String, String> chunkTransformer;
	
	public TextAgentResponseHandler(final String key) {
		this(key, null, null);
	}
	
	public TextAgentResponseHandler(final String key, final Function<String, String> transformer, final Function<String, String> chunkTransformer) {
		this.key = key;
		this.chunkKey = key.concat("_chunk");
		this.transformer = transformer;
		this.chunkTransformer = chunkTransformer;
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
	}
	
	@Override
	public boolean handleChunk(final String chunk_, final AgentContext context) {
		var chunk = chunk_;
		if (this.chunkTransformer != null) {
			chunk = this.chunkTransformer.apply(chunk);
		}
		context.setStringValue(this.chunkKey, chunk);
		context.setStringValue(this.key, Objects.requireNonNullElse(context.getStringValue(this.key), "".concat(chunk)));
		context.setStringValue(UPDATE_KEY, this.key);
		return true;
	}
	
}
