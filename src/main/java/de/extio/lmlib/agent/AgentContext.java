package de.extio.lmlib.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.extio.lmlib.client.Conversation;

public final class AgentContext {
	
	private final Map<String, Agent> agents;
	
	private final Map<String, List<Object>> context;
	
	private final AgentRequestStatistic requestStatistic;
	
	private final List<String> graph = Collections.synchronizedList(new ArrayList<>());
	
	private volatile Conversation conversation;
	
	private volatile AgentNext nextAgent;
	
	public AgentContext(final Map<String, Agent> agents) {
		this.agents = agents;
		this.context = new ConcurrentHashMap<>();
		this.requestStatistic = new AgentRequestStatistic();
	}
	
	public AgentContext(final AgentContext other) {
		this(other, new ConcurrentHashMap<>(other.context));
	}
	
	public AgentContext(final AgentContext other, final Map<String, List<Object>> context) {
		this.agents = other.agents;
		this.context = context;
		this.conversation = Conversation.copy(other.conversation);
		this.nextAgent = other.nextAgent;
		this.requestStatistic = other.requestStatistic;
		synchronized (other.graph) {
			this.graph.addAll(other.graph);
		}
	}
	
	public Map<String, List<Object>> getContext() {
		return this.context;
	}
	
	public Map<String, Agent> getAgents() {
		return this.agents;
	}
	
	public AgentRequestStatistic getRequestStatistic() {
		return this.requestStatistic;
	}
	
	public Conversation getConversation() {
		return this.conversation;
	}
	
	public void setConversation(final Conversation conversation) {
		this.conversation = conversation;
	}
	
	public AgentNext getNextAgent() {
		return this.nextAgent;
	}
	
	public void setNextAgent(final AgentNext next) {
		this.nextAgent = next;
	}
	
	public List<String> getGraph() {
		return this.graph;
	}
	
}
