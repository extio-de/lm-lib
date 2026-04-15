package de.extio.lmlib.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import de.extio.lmlib.agent.responsehandler.AgentResponseHandler;
import de.extio.lmlib.agent.responsehandler.StreamedAgentResponseHandler;
import de.extio.lmlib.agent.responsehandler.TextAgentResponseHandler;
import de.extio.lmlib.agent.responsehandler.ToolCallingAgentResponseHandler;
import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.ClientService;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.ToolCallData;
import de.extio.lmlib.client.ToolDefinition;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.Conversation.TurnType;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;

public interface BaseAgent {
	
	static final Logger LOGGER = LoggerFactory.getLogger(BaseAgent.class);
	
	static final ObjectMapper objectMapper = new ObjectMapper();
	
	default String name() {
		throw new UnsupportedOperationException("Name must be implemented in the agent");
	}
	
	default AgentType agentType(final AgentContext context) {
		throw new UnsupportedOperationException("Agent type must be implemented in the agent");
	}
	
	default ModelCategory modelCategory(final AgentContext context) {
		return ModelCategory.MEDIUM;
	}

	default ModelProfile modelProfile(final AgentContext context) {
		return null;
	}
	
	default String systemPrompt() {
		return null;
	}
	
	default String textTemplate() {
		return null;
	}

	default List<ToolDefinition> toolDefinitions(final AgentContext context) {
		return null;
	}

	default ToolCallData toolCallData(final AgentContext context) {
		final var toolDefinitions = this.toolDefinitions(context);
		if (toolDefinitions == null || toolDefinitions.isEmpty()) {
			final var rememberedToolCallData = context.getToolCallData();
			if (rememberedToolCallData == null || !rememberedToolCallData.hasTools()) {
				return null;
			}
			return ToolCallData.auto(rememberedToolCallData.tools());
		}
		return ToolCallData.auto(toolDefinitions);
	}

	default boolean supportsToolCalling(final AgentContext context) {
		final var modelProfile = this.modelProfile(context);
		return modelProfile != null && modelProfile.modelProvider() == ModelProvider.OAI_CHAT_COMPLETION;
	}

	default boolean supportsToolCalling(final AgentContext context, final ClientService clientService) {
		if (clientService == null) {
			return this.supportsToolCalling(context);
		}
		final var modelProfile = this.modelProfile(context);
		if (modelProfile != null) {
			return clientService.supportsToolCalling(modelProfile);
		}
		return clientService.supportsToolCalling(this.modelCategory(context));
	}
	
	default AgentResponseHandler responseHandler(final AgentContext context) {
		return new TextAgentResponseHandler("response");
	}
	
	default void preProcess(final AgentContext context) {
		
	}
	
	default void postProcess(final AgentContext context) {
		
	}
	
	default List<AgentContext> merge(final List<AgentContext> contexts) {
		return null;
	}
	
	default AgentNext chooseNextAgent(final AgentContext context) {
		return AgentNext.END;
	}
	
	default List<AgentContext> execute(final Client client, final ExecutorService agentExecutorService, final AgentContext context) {
		final var splits = new ArrayList<Split>();
		
		context.setCurrentAgentName(this.name());
		context.setNextAgent(null);
		context.clearError();
		
		try {
			this.preProcess(context);
			if (context.getAgentContextUpdateConsumer() != null) {
				context.getAgentContextUpdateConsumer().accept(context);
			}
		}
		catch (final Exception ex) {
			LOGGER.error("Error during agent execution", ex);
			context.setError(AgentErrorType.GENERAL, ex);
			return List.of(context);
		}
		final boolean skipCompletion = context.isSkipNextCompletion();

		splits.addAll(this.applyTemplate(context));
		final var tasks = new ArrayList<CompletableFuture<?>>(splits.size());
		for (final var split : splits) {
			tasks.add(CompletableFuture.runAsync(() -> {
				try {
					split.context().clearError();
					final boolean skipCompletionInSplit = skipCompletion || this.agentType(split.context()) == AgentType.PROCESSING_ONLY;
					if (skipCompletionInSplit) {
						split.context().setSkipNextCompletion(false);
						split.context().getGraph().add("○");
					}
					else {
						final var modelProfile = this.modelProfile(split.context());
						split.context().getGraph().add(modelProfile != null ? modelProfile.category() : this.modelCategory(split.context()).getShortName());
					}
					split.context().getGraph().add(this.name());
					
					if (!skipCompletionInSplit) {
						final var completionClient = Objects.requireNonNull(client);
						final boolean skipCache = split.context().isSkipCache() || split.context().isAlwaysSkipCache();
						split.context().setSkipCache(false);
						final var conversation = this.setupConversation(split);
						final var toolCallData = completionClient.supportsToolCalling() ? this.toolCallData(split.context()) : null;
						this.rememberToolCallData(split.context(), toolCallData);
						
						boolean parseable = false;
						Exception parseException = null;
						int parseAttempts = 0;
						int toolCallRounds = 0;
						while (parseAttempts < 2) {
							LOGGER.debug("Conversation: {}", conversation);	
							
							final var modelProfile = this.modelProfile(split.context());
							final var responseHandler = this.responseHandler(split.context());
							long requestStart = 0;
							Completion completion = null;
							if (split.context().isStreaming()) {
								if (responseHandler instanceof final StreamedAgentResponseHandler streamedResponseHandler) {
									streamedResponseHandler.beforeStream(split.context());
								}
								final Consumer<Chunk> chunkConsumer = chunk -> {
									if (responseHandler instanceof final StreamedAgentResponseHandler streamedResponseHandler) {
										if (streamedResponseHandler.handleChunk(chunk, split.context()) && split.context().getAgentContextUpdateConsumer() != null) {
											split.context().getAgentContextUpdateConsumer().accept(split.context());
										}
									}
								};
								if (modelProfile != null) {
									requestStart = System.currentTimeMillis();
										completion = completionClient.streamConversation(modelProfile, conversation, chunkConsumer, toolCallData, skipCache);
								}
								else {
									requestStart = System.currentTimeMillis();
										completion = completionClient.streamConversation(this.modelCategory(split.context()), conversation, chunkConsumer, toolCallData, skipCache);
								}
							}
							else {
								if (modelProfile != null) {
									requestStart = System.currentTimeMillis();
										completion = completionClient.conversation(modelProfile, conversation, toolCallData, skipCache);
								}
								else {
									requestStart = System.currentTimeMillis();
										completion = completionClient.conversation(this.modelCategory(split.context()), conversation, toolCallData, skipCache);
								}
							}
							final var durationMs = System.currentTimeMillis() - requestStart;
							split.context().getGraph().add("(" + durationMs + " ms)");
							split.context().setLastCompletion(completion);
							split.context().getRequestStatistic().add(completion);
							if (!completion.toolCalls().isEmpty() && responseHandler instanceof final ToolCallingAgentResponseHandler toolCallingAgentResponseHandler) {
								if (toolCallRounds >= 4) {
									parseException = new IllegalStateException("Too many tool call rounds");
									break;
								}
								final var toolCallResults = new ToolCallingAgentResponseHandler.ToolCallResults();
								if (toolCallingAgentResponseHandler.handleToolCalls(completion, split.context(), toolCallResults)) {
									toolCallRounds++;
									this.appendToolCallResults(conversation, completion, toolCallResults.results());
									continue;
								}
							}
							
							try {
								if (!responseHandler.handle(completion, split.context())) {
									split.context().getGraph().add("⚠");
									LOGGER.warn("{} Cannot parse response: {}", this.name(), completion.response());
									split.context().setSkipCache(true);
									parseAttempts++;
									continue;
								}
								
								parseable = true;
								break;
							}
							catch (final Exception e) {
								parseException = e;
								split.context().getGraph().add("⚠");
								LOGGER.warn("{} Cannot parse response: {}", this.name(), completion.response(), e);
								split.context().setSkipCache(true);
								parseAttempts++;
								continue;
							}
						}
						if (!parseable) {
							LOGGER.warn("{} Response is still not parseable after last attempt", this.name());
							split.context().getGraph().add("☢");
							split.context().setError(AgentErrorType.PARSING, parseException);
							return;
						}
					}
					
					this.postProcess(split.context());
					if (split.context().getAgentContextUpdateConsumer() != null) {
						split.context().getAgentContextUpdateConsumer().accept(split.context());
					}
				}
				catch (final Exception ex) {
					LOGGER.error("Error during agent execution", ex);
					split.context().setError(AgentErrorType.GENERAL, ex);
				}
			}, agentExecutorService));
		}
		
		CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
		
		var retContexts = splits
				.stream()
				.filter(s -> !s.context().isError())
				.sorted((s0, s1) -> Integer.compare(s0.index(), s1.index()))
				.map(Split::context)
				.collect(Collectors.toCollection(ArrayList::new));
		if (!retContexts.isEmpty()) {
			final var merged = this.merge(List.copyOf(retContexts));
			if (merged != null) {
				final var size = retContexts.size();
				retContexts = new ArrayList<>(merged);
				for (final var retContext : retContexts) {
					retContext.getGraph().add(size + "↣" + retContexts.size());
				}
			}
			
			retContexts.forEach(ctx -> this.nextAgent(ctx, skipCompletion));
		}
		
		retContexts.addAll(splits.stream().filter(s -> s.context().isError()).map(s -> s.context()).toList());
		
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

	private void appendToolCallResults(final Conversation conversation, final Completion completion, final List<ToolCallingAgentResponseHandler.ToolCallResult> toolCallResults) {
		conversation.appendToolCallRound(completion, toolCallResults.stream()
				.map(toolCallResult -> new Conversation.ToolResult(toolCallResult.output(), toolCallResult.toolCallId()))
				.toList());
	}
	
	private List<Split> applyTemplate(final AgentContext context) {
		String text = this.textTemplate() != null ? this.textTemplate() : "";
		for (final var entry : context.getContext().entrySet()) {
			var k = "{{{" + entry.getKey() + "}}}";
			if (text.contains(k)) {
				try {
					text = text.replace(k, objectMapper.writeValueAsString(entry.getValue()));
				}
				catch (final JacksonException e) {
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
			final var k0 = "[[" + entry.getKey() + "]]";
			final var k1 = "[[" + entry.getKey() + "|hidden]]";
			if (text.contains(k0) || text.contains(k1)) {
				branchedEntries.add(entry);
				branchedEntriesMinCntValues = branchedEntriesMinCntValues == 0 ? entry.getValue().size() : Math.min(branchedEntriesMinCntValues, entry.getValue().size());
			}
		}
		if (!branchedEntries.isEmpty() && branchedEntriesMinCntValues > 0) {
			final var result = new ArrayList<Split>();
			
			for (int i = 0; i < branchedEntriesMinCntValues; i++) {
				String nextText = text;
				final var nextContext = new HashMap<>(context.getContext());
				for (final var entry : branchedEntries) {
					final var kVisible = "[[" + entry.getKey() + "]]";
					if (nextText.contains(kVisible)) {
						nextText = nextText.replace(kVisible, entry.getValue().get(i).toString());
					}
					final var kHidden = "[[" + entry.getKey() + "|hidden]]";
					if (nextText.contains(kHidden)) {
						// hidden variant participates in branching, but does not reveal the token value in the prompt
						nextText = nextText.replace(kHidden, "");
					}
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
		else if (this.agentType(split.context()) == AgentType.START_CONVERSATION) {
			if (this.systemPrompt() == null || this.systemPrompt().isEmpty()) {
				conversation = Conversation.create(split.text());
			}
			else {
				conversation = Conversation.create(this.systemPrompt() + "\n" + split.text());
			}
			split.context().setConversation(conversation);
		}
		else if (this.agentType(split.context()) == AgentType.START_CONVERSATION_WITH_SYSTEM_PROMPT) {
			conversation = Conversation.create(this.systemPrompt(), split.text());
			split.context().setConversation(conversation);
		}
		else {
			if (conversation.getConversation().get(0).type() == TurnType.SYSTEM && this.agentType(split.context()) != AgentType.CONVERSATION_WITH_SYSTEM_PROMPT) {
				final String mergedSysAndUser;
				if (conversation.getConversation().size() > 1) {
					mergedSysAndUser = conversation.getConversation().get(0).text() + "\n" + conversation.getConversation().get(1).text();
				} else {
					mergedSysAndUser = conversation.getConversation().get(0).text();
				}
				
				final var newConversation = Conversation.create(mergedSysAndUser);
				for (int n = 2; n < conversation.getConversation().size(); n++) {
					newConversation.addTurn(conversation.getConversation().get(n));
				}
				
				split.context().setConversation(newConversation);
				conversation = newConversation;
			}

			if (conversation.getConversation().getLast().type() != TurnType.USER) {
				conversation.addTurn(new Conversation.Turn(TurnType.USER, split.text()));
			}
			else {
				final var lastUserTurn = conversation.getConversation().getLast();
				conversation.replaceTurn(new Turn(TurnType.USER, lastUserTurn.text() + "\n" + split.text()));
			}
		}
		return conversation;
	}

	private void appendLastCompletionToConversation(final AgentContext context, final boolean skipCompletion) {
		if (skipCompletion || context.getConversation() == null || context.getLastCompletion() == null) {
			return;
		}
		final var currentAgent = context.getAgents().get(context.getCurrentAgentName());
		if (currentAgent == null || currentAgent.agentType(context) == AgentType.PROCESSING_ONLY) {
			return;
		}
		if (context.getConversation().getConversation().getLast().type() == TurnType.ASSISTANT) {
			return;
		}
		final var completion = context.getLastCompletion();
		if ((completion.response() == null || completion.response().isEmpty()) && completion.toolCalls().isEmpty()) {
			return;
		}
		context.getConversation().addTurn(new Conversation.Turn(TurnType.ASSISTANT, completion.response() == null ? "" : completion.response(), completion.toolCalls(), null));
	}

	private void rememberToolCallData(final AgentContext context, final ToolCallData toolCallData) {
		if (toolCallData == null || !toolCallData.hasTools()) {
			return;
		}
		LOGGER.debug("Tool calls: {}", toolCallData);
		context.setToolCallData(toolCallData);
	}

	private void clearRememberedToolCallData(final AgentContext context) {
		context.setToolCallData(null);
	}
	
	private void nextAgent(final AgentContext context, final boolean skipCompletion) {
		context.setNextAgent(this.chooseNextAgent(context));
		
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
				
				case final BaseAgent a when a.agentType(context) == AgentType.COMPLETION -> {
					context.getGraph().add("→");
					context.setConversation(null);
					this.clearRememberedToolCallData(context);
				}
				
				case final BaseAgent a when a.agentType(context) == AgentType.START_CONVERSATION || a.agentType(context) == AgentType.START_CONVERSATION_WITH_SYSTEM_PROMPT -> {
					context.getGraph().add("🗨");
					context.setConversation(null);
					this.clearRememberedToolCallData(context);
				}
				
				case final BaseAgent a when a.agentType(context) == AgentType.CONVERSATION || a.agentType(context) == AgentType.CONVERSATION_WITH_SYSTEM_PROMPT -> {
					if (context.getConversation() == null) {
						context.getGraph().add("🗨");
						context.setConversation(null);
					}
					else {
						context.getGraph().add("↴");
						this.appendLastCompletionToConversation(context, skipCompletion);
					}
				}
				
				case final BaseAgent a when a.agentType(context) == AgentType.PROCESSING_ONLY -> {
					this.appendLastCompletionToConversation(context, skipCompletion);
				}
				
				default -> {
					context.getGraph().add("☢");
					LOGGER.error("Unknown agent type: {}", nextAgent.agentType(context));
					context.setNextAgent(null);
				}
			}
		}
	}
	
}
