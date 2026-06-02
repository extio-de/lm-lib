package de.extio.lmlib.agent;

public enum AgentSkipCompletion {
	
	/** Execute the LLM call normally */
	NO_SKIP,
	
	/** Only set up the conversation without executing the LLM call */
	SETUP_CONVERSATION_ONLY,
	
	/** Skip the turn entirely */
	SKIP
}
