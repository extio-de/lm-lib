package de.extio.lmlib.agent.responsehandler;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Completion;

public interface AgentResponseHandler {
	
	boolean handle(Completion completion, AgentContext context);
}
