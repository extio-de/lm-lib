package de.extio.lmlib.agent.responsehandler;

import de.extio.lmlib.agent.AgentContext;

public interface StreamedAgentResponseHandler extends AgentResponseHandler {
    
    void beforeStream(AgentContext context);

    boolean handleChunk(String chunk, AgentContext context);
    
}
