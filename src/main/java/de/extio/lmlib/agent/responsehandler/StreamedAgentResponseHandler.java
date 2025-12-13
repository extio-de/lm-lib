package de.extio.lmlib.agent.responsehandler;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Chunk;

public interface StreamedAgentResponseHandler extends AgentResponseHandler {
	
	void beforeStream(AgentContext context);

	boolean handleChunk(Chunk chunk, AgentContext context);
	
}
