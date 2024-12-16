package de.extio.lmlib.agent;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.extio.lmlib.client.Completion;

public class TextAgentResponseHandler implements AgentResponseHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TextAgentResponseHandler.class);
	
	private final String key;
	
	public TextAgentResponseHandler(final String key) {
		this.key = key;
	}
	
	@Override
	public boolean handle(final Split split, final Completion completion) {
		split.context().getContext().put(this.key, List.of(completion.response()));
		LOGGER.debug("Response: {}", completion.response());
		return true;
	}
	
}
