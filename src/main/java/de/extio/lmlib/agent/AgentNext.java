package de.extio.lmlib.agent;

public final record AgentNext(String name, Boolean gradingPassed) {
	
    public static final AgentNext END = new AgentNext(null, null);

}
