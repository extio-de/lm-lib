package de.extio.lmlib.agent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;

public final class AgentContext {
	
	// Exposed
	
	private final Map<String, List<? extends Object>> context;
	
	private volatile boolean streaming;
	
	private volatile AgentErrorType errorType;

	private volatile Exception errorException;
	
	private volatile boolean skipNextCompletion;

	private volatile boolean skipCache;
	
	private volatile Conversation conversation;
	
	private final AgentRequestStatistic requestStatistic;
	
	private final List<String> graph = Collections.synchronizedList(new ArrayList<>());
	
	// Internal
	
	private final Map<String, ? extends BaseAgent> agents;
	
	private volatile AgentNext nextAgent;
	
	private volatile Completion lastCompletion;
	
	private Consumer<AgentContext> agentContextUpdateConsumer;
	
	public AgentContext() {
		this.agents = new HashMap<>();
		this.context = new ConcurrentHashMap<>();
		this.requestStatistic = AgentRequestStatistic.create();
	}
	
	public AgentContext(final Collection<? extends BaseAgent> agents) {
		this.agents = agents.stream().collect(Collectors.toMap(BaseAgent::name, agent -> agent));
		this.context = new ConcurrentHashMap<>();
		this.requestStatistic = AgentRequestStatistic.create();
	}
	
	public AgentContext(final Map<String, ? extends BaseAgent> agents) {
		this.agents = agents;
		this.context = new ConcurrentHashMap<>();
		this.requestStatistic = AgentRequestStatistic.create();
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
		this.agentContextUpdateConsumer = other.agentContextUpdateConsumer;
		this.errorType = other.errorType;
		this.errorException = other.errorException;
		this.skipNextCompletion = other.skipNextCompletion;
		this.skipCache = other.skipCache;
		this.streaming = other.streaming;
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
		if (value == null) {
			this.context.remove(key);
		} else {
			this.context.put(key, List.of(value));
		}
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
	
	public <T> T getValue(final String key, final Class<T> type) {
		final var values = this.context.get(key);
		if (values == null || values.isEmpty()) {
			return null;
		}
		final var firstValue = values.getFirst();
		if (type.isInstance(firstValue)) {
			return type.cast(firstValue);
		}
		throw new IllegalArgumentException("Value for key '" + key + "' is not of type " + type.getName());
	}

	public <T> List<T> getValues(final String key, final Class<T> type) {
		final var values = this.context.get(key);
		if (values == null) {
			return null;
		}
		return values.stream().map(type::cast).collect(Collectors.toCollection(ArrayList::new));
	}
	
	public <T> void setValue(final String key, final T value) {
		if (value == null) {
			this.context.remove(key);
		}
		else {
			this.context.put(key, List.of(value));
		}
	}

	public <T> void setValues(final String key, final Collection<T> values) {
		if (values == null) {
			this.context.remove(key);
		}
		else {
			this.context.put(key, List.copyOf(values));
		}
	}
	
	public Map<String, List<? extends Object>> getContext() {
		return this.context;
	}
	
	Map<String, ? extends BaseAgent> getAgents() {
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
	
	AgentNext getNextAgent() {
		return this.nextAgent;
	}
	
	void setNextAgent(final AgentNext next) {
		this.nextAgent = next;
	}
	
	public List<String> getGraph() {
		return this.graph;
	}
	
	Completion getLastCompletion() {
		return this.lastCompletion;
	}
	
	void setLastCompletion(final Completion lastCompletion) {
		this.lastCompletion = lastCompletion;
	}
	
	public boolean isError() {
		return this.errorType != null;
	}
	
	public AgentErrorType getErrorType() {
		return this.errorType;
	}
	
	public Exception getErrorException() {
		return this.errorException;
	}
	
	public void setError(final AgentErrorType errorType, final Exception errorException) {
		if (errorType == null) {
			this.clearError();
			return;
		}
		this.errorType = errorType;
		this.errorException = errorException;
	}
	
	public void clearError() {
		this.errorType = null;
		this.errorException = null;
	}
	
	public boolean isSkipNextCompletion() {
		return this.skipNextCompletion;
	}
	
	public void setSkipNextCompletion(final boolean skipNextCompletion) {
		this.skipNextCompletion = skipNextCompletion;
	}

	public boolean isSkipCache() {
		return this.skipCache;
	}

	public void setSkipCache(final boolean skipCache) {
		this.skipCache = skipCache;
	}
	
	Consumer<AgentContext> getAgentContextUpdateConsumer() {
		return agentContextUpdateConsumer;
	}
	
	void setAgentContextUpdateConsumer(final Consumer<AgentContext> agentContextUpdateConsumer) {
		this.agentContextUpdateConsumer = agentContextUpdateConsumer;
	}
	
	public boolean isStreaming() {
		return streaming;
	}
	
	public void setStreaming(final boolean streaming) {
		this.streaming = streaming;
	}
}
