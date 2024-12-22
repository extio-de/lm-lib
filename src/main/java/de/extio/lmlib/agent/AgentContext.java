package de.extio.lmlib.agent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;

public final class AgentContext {
	
	private final Map<String, Agent> agents;
	
	private final Map<String, List<? extends Object>> context;
	
	private final AgentRequestStatistic requestStatistic;
	
	private final List<String> graph = Collections.synchronizedList(new ArrayList<>());
	
	private volatile Conversation conversation;
	
	private volatile AgentNext nextAgent;
	
	private volatile Completion lastCompletion;
	
	private volatile boolean error;
	
	public AgentContext(final Map<String, Agent> agents) {
		this.agents = agents;
		this.context = new ConcurrentHashMap<>();
		this.requestStatistic = new AgentRequestStatistic();
	}
	
	public AgentContext(final AgentContext other) {
		this(other, new ConcurrentHashMap<>(other.context));
	}
	
	public AgentContext(final AgentContext other, final Map<String, List<? extends Object>> context) {
		this.agents = other.agents;
		this.context = context;
		this.conversation = Conversation.copy(other.conversation);
		this.nextAgent = other.nextAgent;
		this.requestStatistic = other.requestStatistic;
		this.lastCompletion = other.lastCompletion;
		this.error = other.error;
		synchronized (other.graph) {
			this.graph.addAll(other.graph);
		}
	}
	
	public String getStringValue(final String key) {
		final var values = this.context.get(key);
		if (values == null || values.isEmpty()) {
			return null;
		}
		return values.getFirst().toString();
	}
	
	public void setStringValue(final String key, final String value) {
		this.context.put(key, List.of(value));
	}
	
	public List<String> getStringValues(final String key) {
		final var values = this.context.get(key);
		if (values == null) {
			return null;
		}
		return values.stream().map(Objects::toString).collect(Collectors.toCollection(ArrayList::new));
	}
	
	public void setStringValues(final String key, final Collection<String> value) {
		this.context.put(key, List.copyOf(value));
	}
	
	public Map<String, List<? extends Object>> getContext() {
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
	
	public Completion getLastCompletion() {
		return this.lastCompletion;
	}
	
	public void setLastCompletion(final Completion lastCompletion) {
		this.lastCompletion = lastCompletion;
	}
	
	public boolean isError() {
		return this.error;
	}
	
	public void setError(final boolean error) {
		this.error = error;
	}
	
}
