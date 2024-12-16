package de.extio.lmlib.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.TurnType;
import de.extio.lmlib.client.profile.ModelCategory;

public record Agent(String name, AgentType agentType, ModelCategory modelCategory, String systemPrompt, String textTemplate, AgentResponseHandler responseHandler, Consumer<AgentContext> preProcessor, Consumer<AgentContext> postProcessor, Function<List<AgentContext>, List<AgentContext>> merger, Function<AgentContext, AgentNext> chooseNext) {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Agent.class);
	
	public List<AgentContext> execute(final Client client, final ExecutorService agentExecutorService, final AgentContext context) {
		final var retContexts = Collections.synchronizedList(new ArrayList<AgentContext>());
		
		context.setNextAgent(null);
		
		if (this.preProcessor != null) {
			this.preProcessor.accept(context);
		}
		
		final var splits = this.applyTemplate(context);
		final var tasks = new ArrayList<CompletableFuture<?>>(splits.size());
		for (final var split : splits) {
			tasks.add(CompletableFuture.runAsync(() -> {
				split.context().getGraph().add(this.modelCategory.getShortName());
				split.context().getGraph().add(this.name());
				
				final var conversation = this.setupConversation(split);
				LOGGER.debug("Conversation: {}", conversation);
				
				Completion completion = null;
				boolean parseable = false;
				for (int i = 0; i < 2; i++) {
					completion = client.conversation(this.modelCategory, conversation);
					split.context().getRequestStatistic().add(completion);
					
					try {
						if (!this.responseHandler.handle(split, completion)) {
							context.getGraph().add("⚠");
							LOGGER.warn("{} Cannot parse response: {}", this.name(), completion.response());
							continue;
						}
						
						parseable = true;
						break;
					}
					catch (final Exception e) {
						context.getGraph().add("⚠");
						LOGGER.warn("{} Cannot parse response: {}", this.name(), completion.response(), e);
						continue;
					}
				}
				if (!parseable) {
					LOGGER.warn("{} Response is still not parseable after last attempt", this.name());
					context.getGraph().add("☢");
					return;
				}
				
				if (this.postProcessor != null) {
					this.postProcessor.accept(split.context());
				}
				
				this.chooseNextAgent(conversation, completion, split.context());
				
				retContexts.add(split.context());
			}, agentExecutorService));
		}
		
		CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
		
		if (this.merger != null) {
			final var mergedContexts = this.merger.apply(retContexts);
			mergedContexts.forEach(c -> c.getGraph().add(retContexts.size() + "↣" + mergedContexts.size()));
			return mergedContexts;
		}
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
		String text = this.textTemplate;
		for (final var entry : context.getContext().entrySet()) {
			final var k = "{{" + entry.getKey() + "}}";
			if (text.contains(k)) {
				text = text.replace(k, entry.getValue().stream().map(Object::toString).collect(Collectors.joining("\n\n")));
			}
		}
		
		for (final var entry : context.getContext().entrySet()) {
			final var k = "[[" + entry.getKey() + "]]";
			if (text.contains(k)) {
				final var result = new ArrayList<Split>();
				
				int i = 0;
				for (final var value : entry.getValue()) {
					final var nextText = text.replace(k, value.toString());
					
					final var nextContext = new HashMap<>(context.getContext());
					nextContext.put(entry.getKey(), List.of(value));
					
					final var nextAgentContext = new AgentContext(context, nextContext);
					if (entry.getValue().size() > 1) {
						nextAgentContext.getGraph().add("⇉ " + ++i + "/" + entry.getValue().size());
					}
					
					result.add(new Split(nextText, nextAgentContext));
				}
				
				return result; // TODO: Multiple splits not supported yet
			}
		}
		
		return List.of(new Split(text, context));
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
			if (conversation.getConversation().get(0).type() == TurnType.SYSTEM) {
				final var mergedSysAndUser = conversation.getConversation().get(0).text() + "\n" + conversation.getConversation().get(1).text();
				
				final var newConversation = Conversation.create(mergedSysAndUser);
				for (int n = 2; n < conversation.getConversation().size(); n++) {
					newConversation.addTurn(conversation.getConversation().get(n));
				}
				
				split.context().setConversation(newConversation);
				conversation = newConversation;
			}
			if (conversation.getConversation().get(conversation.getConversation().size() - 1).type() == TurnType.ASSISTANT) {
				conversation.addTurn(new Conversation.Turn(TurnType.USER, String.join("\n", this.systemPrompt, split.text())));
			}
		}
		return conversation;
	}
	
	private void chooseNextAgent(final Conversation conversation, final Completion completion, final AgentContext context) {
		context.setNextAgent(this.chooseNext.apply(context));
		
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
			
			String answer = completion.response();
			if (context.getNextAgent().transformResponse() != null) {
				answer = context.getNextAgent().transformResponse().apply(answer);
			}
			
			final Agent nextAgent = context.getAgents().get(context.getNextAgent().name());
			switch (nextAgent) {
				case null -> {
					context.getGraph().add("☢");
					LOGGER.error("Agent not found: {}", context.getNextAgent().name());
					context.setNextAgent(null);
				}
				
				case final Agent a when context.getNextAgent().responseAsUserPrompt() == Boolean.TRUE -> {
					context.getGraph().add("↺");
					conversation.replaceTurn(new Conversation.Turn(TurnType.USER, answer));
				}
				
				case final Agent a when a.agentType() == AgentType.COMPLETION -> {
					context.getGraph().add("→");
					context.setConversation(null);
				}
				
				case final Agent a when a.agentType() == AgentType.START_CONVERSATION -> {
					context.getGraph().add("🗨");
					context.setConversation(null);
				}
				
				case final Agent a when a.agentType() == AgentType.CONVERSATION -> {
					context.getGraph().add("↴");
					conversation.addTurn(new Conversation.Turn(TurnType.ASSISTANT, answer));
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
