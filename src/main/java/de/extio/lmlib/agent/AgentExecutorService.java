package de.extio.lmlib.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.ClientService;

public class AgentExecutorService implements InitializingBean, DisposableBean {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AgentExecutorService.class);
	
	@Value("${agent.threads:2}")
	private int threads;
	
	@Autowired
	private ClientService clientService;
	
	@Autowired(required = false)
	private List<BaseAgent> allAgents;
	
	private ExecutorService branchExecutorService;
	
	private ExecutorService agentExecutorService;
	
	@Override
	public void afterPropertiesSet() throws Exception {
		this.branchExecutorService = Executors.newVirtualThreadPerTaskExecutor();
		this.agentExecutorService = Executors.newFixedThreadPool(this.threads);
	}
	
	@Override
	public void destroy() throws Exception {
		this.branchExecutorService.close();
		this.agentExecutorService.close();
	}
	
	public List<AgentContext> walk(final String firstAgent, final AgentContext context) {
		return this.walk(firstAgent, context, null);
	}
	
	public List<AgentContext> walk(final String firstAgent, final AgentContext context, final Consumer<AgentContext> agentContextUpdateConsumer) {
		this.discoverAgents(context);
		final var agent = context.getAgents().get(firstAgent);
		if (agent == null) {
			throw new IllegalArgumentException("Agent not found: " + firstAgent);
		}
		
		return this.walk(agent, context, agentContextUpdateConsumer);
	}
	
	public List<AgentContext> walk(final BaseAgent agent, final AgentContext context) {
		return this.walk(agent, context, null);
	}
	
	public List<AgentContext> walk(final BaseAgent agent, final AgentContext context, final Consumer<AgentContext> agentContextUpdateConsumer) {
		if (!context.getGraph().isEmpty()) {
			context.getGraph().add("‖");
		}
		return this.walkRecursive(agent, context, agentContextUpdateConsumer);
	}
	
	private List<AgentContext> walkRecursive(final BaseAgent agent, final AgentContext context, final Consumer<AgentContext> agentContextUpdateConsumer) {
		LOGGER.debug("Agent: {}", agent.name());
		
		this.discoverAgents(context);
		
		Client client = null;
		if (agent.agentType(context) != AgentType.PROCESSING_ONLY) {
			final var modelProfile = agent.modelProfile(context);
			if (modelProfile != null) {
				client = this.clientService.getClient(modelProfile);
			}
			else {
				client = this.clientService.getClient(agent.modelCategory(context));
			}
		}
		context.setAgentContextUpdateConsumer(agentContextUpdateConsumer);
		
		final var branches = agent.execute(client, this.agentExecutorService, context);
		
		final var responses = Collections.synchronizedList(new ArrayList<AgentContext>());
		final var branchTasks = new ArrayList<CompletableFuture<?>>(branches.size());
		for (final var branchContext : branches) {
			if (agentContextUpdateConsumer != null) {
				agentContextUpdateConsumer.accept(branchContext);
			}
			
			if (!branchContext.isError() && branchContext.getNextAgent() != null) {
				final var branchAgent = branchContext.getAgents().get(branchContext.getNextAgent().name());
				if (branchAgent == null) {
					throw new IllegalArgumentException("Agent not found: " + branchContext.getNextAgent().name());
				}
				
				branchTasks.add(CompletableFuture.runAsync(() -> {
					try {
						responses.addAll(this.walkRecursive(branchAgent, branchContext, agentContextUpdateConsumer));
					}
					catch (final Exception e) {
						LOGGER.error("Error executing agent", e);
					}
				}, this.branchExecutorService));
			}
			else {
				responses.add(branchContext);
			}
		}
		
		if (!branchTasks.isEmpty()) {
			CompletableFuture.allOf(branchTasks.toArray(new CompletableFuture[0])).join();
		}
		
		return responses;
	}
	
	private void discoverAgents(final AgentContext context) {
		if (context.getAgents().isEmpty() && this.allAgents != null) {
			for (final var agent : this.allAgents) {
				@SuppressWarnings("unchecked")
				final var agentsMap = (Map<String, BaseAgent>) context.getAgents();
				agentsMap.put(agent.name(), agent);
			}
		}
	}
	
}
