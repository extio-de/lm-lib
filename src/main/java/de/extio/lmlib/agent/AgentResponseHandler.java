package de.extio.lmlib.agent;

import de.extio.lmlib.client.Completion;

public interface AgentResponseHandler {
	
	boolean handle(Split split, Completion completion);
	
}
