package de.extio.lmlib.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.TurnType;
import de.extio.lmlib.profile.ModelCategory;

public interface BaseAgent {
	
	static final Logger LOGGER = LoggerFactory.getLogger(Agent.class);
	
	static final ObjectMapper objectMapper = new ObjectMapper();
	
	default String name() {
		throw new IllegalStateException("Name must be implemented in the agent");
	}
	
	default AgentType agentType() {
		throw new IllegalStateException("Agent type must be implemented in the agent");
	}
	
	default ModelCategory modelCategory() {
		return ModelCategory.MEDIUM;
	}
	
	default String systemPrompt() {
		return "";
	}
	
	default String textTemplate() {
		return "";
	}
	
	default AgentResponseHandler responseHandler() {
		return (split, completion) -> true;
	}
	
	default Consumer<AgentContext> preProcessor() {
		return null;
	}
	
	default Consumer<AgentContext> postProcessor() {
		return null;
	}
	
	default Function<List<AgentContext>, List<AgentContext>> merger() {
		return null;
	}
	
	default Function<AgentContext, AgentNext> chooseNext() {
		return this::chooseNext;
	}
	
	default AgentNext chooseNext(final AgentContext context) {
		return AgentNext.END;
	}
	
	default List<AgentContext> execute(final Client client, final ExecutorService agentExecutorService, final AgentContext context) {
		final var finishedSplits = Collections.synchronizedList(new ArrayList<Split>());
		
		context.setNextAgent(null);
		
		if (this.preProcessor() != null) {
			this.preProcessor().accept(context);
		}
		
		final var splits = this.applyTemplate(context);
		final var tasks = new ArrayList<CompletableFuture<?>>(splits.size());
		for (final var split : splits) {
			tasks.add(CompletableFuture.runAsync(() -> {
				try {
					final boolean skipCompletion = split.context().isSkipNextCompletion() || this.agentType() == AgentType.PROCESSING_ONLY;
					if (skipCompletion) {
						split.context().setSkipNextCompletion(false);
						split.context().getGraph().add("○");
					}
					else {
						split.context().getGraph().add(this.modelCategory().getShortName());
					}
					split.context().getGraph().add(this.name());
					
					if (!skipCompletion) {
						final var conversation = this.setupConversation(split);
						LOGGER.debug("Conversation: {}", conversation);
						
						Completion completion = null;
						boolean parseable = false;
						for (int i = 0; i < 2; i++) {
							completion = client.conversation(this.modelCategory(), conversation);
							split.context().setLastCompletion(completion);
							split.context().getRequestStatistic().add(completion);
							
							try {
								if (!this.responseHandler().handle(split, completion)) {
									split.context().getGraph().add("⚠");
									LOGGER.warn("{} Cannot parse response: {}", this.name(), completion.response());
									continue;
								}
								
								parseable = true;
								break;
							}
							catch (final Exception e) {
								split.context().getGraph().add("⚠");
								LOGGER.warn("{} Cannot parse response: {}", this.name(), completion.response(), e);
								continue;
							}
						}
						if (!parseable) {
							LOGGER.warn("{} Response is still not parseable after last attempt", this.name());
							split.context().getGraph().add("☢");
							split.context().setError(true);
							return;
						}
					}
					
					if (this.postProcessor() != null) {
						this.postProcessor().accept(split.context());
					}
					
					finishedSplits.add(split);
				}
				catch (final Exception ex) {
					LOGGER.error("Error during agent execution", ex);
					split.context().setError(true);
				}
			}, agentExecutorService));
		}
		
		CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
		
		var retContexts = finishedSplits
				.stream()
				.filter(s -> !s.context().isError())
				.sorted((s0, s1) -> Integer.compare(s0.index(), s1.index()))
				.map(Split::context)
				.collect(Collectors.toCollection(ArrayList::new));
		
		if (!retContexts.isEmpty()) {
			if (this.merger() != null) {
				final int size = retContexts.size();
				retContexts = new ArrayList<>(this.merger().apply(retContexts));
				for (final var retContext : retContexts) {
					retContext.getGraph().add(size + "↣" + retContexts.size());
				}
			}
			
			retContexts.forEach(this::nextAgent);
		}
		
		retContexts.addAll(finishedSplits.stream().filter(s -> s.context().isError()).map(s -> s.context()).toList());
		
		return retContexts;
	}
	
	public static List<AgentContext> mergeContexts(final List<String> keys, final List<AgentContext> contexts) {
		final var first = contexts.getFirst();
		for (final var key : keys) {
			first.getContext().compute(key, (k, v) -> {
				final var al = new ArrayList<>();
				if (v != null) {
					al.addAll(v);
				}
				for (int i = 1; i < contexts.size(); i++) {
					final var ctx = contexts.get(i);
					final var val = ctx.getContext().get(key);
					if (val != null) {
						al.addAll(val);
					}
				}
				return al;
			});
		}
		return List.of(first);
	}
	
	private List<Split> applyTemplate(final AgentContext context) {
		String text = this.textTemplate() != null ? this.textTemplate() : "";
		for (final var entry : context.getContext().entrySet()) {
			var k = "{{{" + entry.getKey() + "}}}";
			if (text.contains(k)) {
				try {
					text = text.replace(k, objectMapper.writeValueAsString(entry.getValue()));
				}
				catch (final JsonProcessingException e) {
					LOGGER.warn("Cannot write json", e);
				}
			}
			
			k = "{{" + entry.getKey() + "}}";
			if (text.contains(k)) {
				text = text.replace(k, entry.getValue().stream().map(Object::toString).collect(Collectors.joining("\n\n")));
			}
		}
		
		var branchedEntriesMinCntValues = 0;
		final var branchedEntries = new ArrayList<Entry<String, List<? extends Object>>>();
		for (final var entry : context.getContext().entrySet()) {
			final var k = "[[" + entry.getKey() + "]]";
			if (text.contains(k)) {
				branchedEntries.add(entry);
				branchedEntriesMinCntValues = branchedEntriesMinCntValues == 0 ? entry.getValue().size() : Math.min(branchedEntriesMinCntValues, entry.getValue().size());
			}
		}
		if (!branchedEntries.isEmpty()) {
			final var result = new ArrayList<Split>();
			
			for (int i = 0; i < branchedEntriesMinCntValues; i++) {
				String nextText = text;
				final var nextContext = new HashMap<>(context.getContext());
				for (final var entry : branchedEntries) {
					final var k = "[[" + entry.getKey() + "]]";
					nextText = nextText.replace(k, entry.getValue().get(i).toString());
					nextContext.put(entry.getKey(), List.of(entry.getValue().get(i)));
				}
				
				final var nextAgentContext = new AgentContext(context, nextContext);
				if (branchedEntriesMinCntValues > 1) {
					nextAgentContext.getGraph().add("⇉ " + (i + 1) + "/" + branchedEntriesMinCntValues);
				}
				
				result.add(new Split(nextText, nextAgentContext, i));
			}
			
			return result;
		}
		
		return List.of(new Split(text, context, 0));
	}
	
	private Conversation setupConversation(final Split split) {
		var conversation = split.context().getConversation();
		if (conversation == null || conversation.getConversation().isEmpty()) {
			conversation = Conversation.create(this.systemPrompt(), split.text());
			split.context().setConversation(conversation);
		}
		else if (this.agentType() == AgentType.START_CONVERSATION) {
			conversation = Conversation.create(this.systemPrompt() + "\n" + split.text());
			split.context().setConversation(conversation);
		}
		else {
			if (conversation.getConversation().get(0).type() == TurnType.SYSTEM && this.agentType() != AgentType.CONVERSATION_WITH_SYSTEM_PROMPT) {
				final var mergedSysAndUser = conversation.getConversation().get(0).text() + "\n" + conversation.getConversation().get(1).text();
				
				final var newConversation = Conversation.create(mergedSysAndUser);
				for (int n = 2; n < conversation.getConversation().size(); n++) {
					newConversation.addTurn(conversation.getConversation().get(n));
				}
				
				split.context().setConversation(newConversation);
				conversation = newConversation;
			}
			if (conversation.getConversation().get(conversation.getConversation().size() - 1).type() == TurnType.ASSISTANT) {
				conversation.addTurn(new Conversation.Turn(TurnType.USER, String.join("\n", this.systemPrompt(), split.text())));
			}
		}
		return conversation;
	}
	
	private void nextAgent(final AgentContext context) {
		context.setNextAgent(this.chooseNext().apply(context));
		
		if (context.getNextAgent() != null) {
			if (context.getNextAgent().gradingPassed() != null) {
				if (context.getNextAgent().gradingPassed()) {
					context.getGraph().add("✓");
				}
				else {
					context.getGraph().add("✗");
				}
			}
			
			if (context.getNextAgent().name() == null) {
				context.setNextAgent(null);
				return;
			}
			
			final BaseAgent nextAgent = context.getAgents().get(context.getNextAgent().name());
			switch (nextAgent) {
				case null -> {
					context.getGraph().add("☢");
					LOGGER.error("Agent not found: {}", context.getNextAgent().name());
					context.setNextAgent(null);
				}
				
				case final BaseAgent a when a.agentType() == AgentType.COMPLETION -> {
					context.getGraph().add("→");
					context.setConversation(null);
				}
				
				case final BaseAgent a when a.agentType() == AgentType.START_CONVERSATION -> {
					context.getGraph().add("🗨");
					context.setConversation(null);
				}
				
				case final BaseAgent a when a.agentType() == AgentType.CONVERSATION || a.agentType() == AgentType.CONVERSATION_WITH_SYSTEM_PROMPT -> {
					context.getGraph().add("↴");
					if (context.getConversation().getConversation().getLast().type() != TurnType.ASSISTANT) {
						context.getConversation().addTurn(new Conversation.Turn(TurnType.ASSISTANT, context.getLastCompletion().response()));
					}
				}
				
				case final BaseAgent a when a.agentType() == AgentType.PROCESSING_ONLY -> {
					
				}
				
				default -> {
					context.getGraph().add("☢");
					LOGGER.error("Unknown agent type: {}", nextAgent.agentType());
					context.setNextAgent(null);
				}
			}
		}
	}
	
}
