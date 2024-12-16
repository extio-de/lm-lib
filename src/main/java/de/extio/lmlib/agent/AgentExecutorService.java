package de.extio.lmlib.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.extio.lmlib.client.ClientService;

@Service
public class AgentExecutorService implements InitializingBean, DisposableBean {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AgentExecutorService.class);
	
	@Value("${agent.threads:2}")
	private int threads;
	
	@Autowired
	private ClientService clientService;
	
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
	
	public List<AgentContext> walkGraph(final Agent agent, final AgentContext context) {
		LOGGER.info("Agent: {}", agent.name());
		
		final var client = this.clientService.getClient(agent.modelCategory());
		final var responses = Collections.synchronizedList(new ArrayList<AgentContext>());
		
		final var branches = agent.execute(client, this.agentExecutorService, context);
		final var branchTasks = new ArrayList<CompletableFuture<?>>(branches.size());
		for (final var branchContext : branches) {
			if (branchContext.getNextAgent() != null) {
				final var branchAgent = context.getAgents().get(branchContext.getNextAgent().name());
				if (branchAgent == null) {
					throw new IllegalArgumentException("Agent not found: " + branchContext.getNextAgent().name());
				}
				
				branchTasks.add(CompletableFuture.runAsync(() -> {
					responses.addAll(this.walkGraph(branchAgent, branchContext));
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
	
}
