package de.extio.lmlib.agent.responsehandler;

import java.util.ArrayList;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Completion;

public class AccumulateTextAgentResponseHandler implements AgentResponseHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AccumulateTextAgentResponseHandler.class);
	
	private final String key;
	
	private final Function<String, String> transformer;
	
	public AccumulateTextAgentResponseHandler(final String key) {
		this(key, null);
	}
	
	public AccumulateTextAgentResponseHandler(final String key, final Function<String, String> transformer) {
		this.key = key;
		this.transformer = transformer;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public boolean handle(final Completion completion, final AgentContext context) {
		var response = completion.response();
		LOGGER.debug("Response: {}", response);
		if (this.transformer != null) {
			response = this.transformer.apply(response);
		}
		((ArrayList<Object>) context.getContext().computeIfAbsent(this.key, k -> new ArrayList<>())).add(response);
		return true;
	}
	
}
