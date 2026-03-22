package de.extio.lmlib.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import de.extio.lmlib.agent.responsehandler.AgentResponseHandler;
import de.extio.lmlib.agent.responsehandler.TextAgentResponseHandler;
import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.CompletionStatistics;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.ToolCall;
import de.extio.lmlib.client.ToolCallData;
import de.extio.lmlib.client.ToolDefinition;
import de.extio.lmlib.client.ToolParameters;
import de.extio.lmlib.profile.ModelCategory;

class AgentErrorHandlingTest {

	@Test
	void parsingErrorsKeepTheirTypeWithoutAnExceptionWhenTheHandlerReturnsFalse() {
		final Client client = mock(Client.class);
		when(client.conversation(any(ModelCategory.class), any(), isNull(), anyBoolean())).thenReturn(new Completion("not json", null, null, new CompletionStatistics(1, Duration.ofMillis(1), 1, 0, 1, 0, BigDecimal.ZERO, false)));
		
		final AgentResponseHandler responseHandler = (completion, context) -> false;
		final var agent = new Agent("ParseAgent",
				AgentType.COMPLETION,
				ModelCategory.MEDIUM,
				null,
				null,
				"prompt",
				null,
				responseHandler,
				null,
				null,
				null,
				AgentNext::end);
		final var context = new AgentContext(Map.of(agent.name(), agent));
		
		try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
			final var results = agent.execute(client, executorService, context);
			final var result = results.getFirst();
			assertTrue(result.isError());
			assertEquals(AgentErrorType.PARSING, result.getErrorType());
			assertNull(result.getErrorException());
		}
	}

	@Test
	void thrownExceptionsAreStoredAsGeneralErrors() {
		final var exception = new IllegalStateException("boom");
		final var agent = new Agent("BrokenAgent",
				AgentType.PROCESSING_ONLY,
				ModelCategory.MEDIUM,
				null,
				null,
				null,
				null,
				null,
				context -> {
					throw exception;
				},
				null,
				null,
				AgentNext::end);
		final var context = new AgentContext(Map.of(agent.name(), agent));
		
		try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
			final var results = agent.execute(null, executorService, context);
			final var result = results.getFirst();
			assertTrue(result.isError());
			assertEquals(AgentErrorType.GENERAL, result.getErrorType());
			assertInstanceOf(IllegalStateException.class, result.getErrorException());
			assertEquals("boom", result.getErrorException().getMessage());
		}
	}

	@Test
	void toolCallDataIsRetainedForFollowUpConversationRequests() {
		final var client = mock(Client.class);
		when(client.supportsToolCalling()).thenReturn(true);
		final var requestCounter = new AtomicInteger();
		final var firstRequestToolCallData = new AtomicReference<ToolCallData>();
		final var secondRequestToolCallData = new AtomicReference<ToolCallData>();
		final var weatherTool = new ToolDefinition(
				"get_weather",
				"Gets the current weather for a given location.",
				ToolParameters.create(Map.of("location", "")),
				true);
		doAnswer(invocation -> {
			final var toolCallData = invocation.getArgument(2, ToolCallData.class);
			final var currentRequest = requestCounter.incrementAndGet();
			if (currentRequest == 1) {
				firstRequestToolCallData.set(toolCallData);
				return new Completion(
						"",
						null,
						CompletionFinishReason.TOOL_CALLS,
						new CompletionStatistics(1, Duration.ofMillis(1), 1, 0, 1, 0, BigDecimal.ZERO, false),
						List.of(new ToolCall("call_1", "function", "get_weather", "{\"location\":\"Berlin, Germany\"}")));
			}
			secondRequestToolCallData.set(toolCallData);
			return new Completion("Berlin is rainy and 21C", null, CompletionFinishReason.DONE, new CompletionStatistics(1, Duration.ofMillis(1), 1, 0, 1, 0, BigDecimal.ZERO, false));
		}).when(client).conversation(any(ModelCategory.class), any(), any(ToolCallData.class), anyBoolean());

		final BaseAgent toolCaller = new BaseAgent() {

			@Override
			public String name() {
				return "ToolCaller";
			}

			@Override
			public AgentType agentType(final AgentContext context) {
				return AgentType.START_CONVERSATION;
			}

			@Override
			public ModelCategory modelCategory(final AgentContext context) {
				return ModelCategory.MEDIUM;
			}

			@Override
			public String systemPrompt() {
				return "You must call the weather tool.";
			}

			@Override
			public String textTemplate() {
				return "What is the weather in Berlin, Germany?";
			}

			@Override
			public List<ToolDefinition> toolDefinitions(final AgentContext context) {
				return List.of(weatherTool);
			}

			@Override
			public ToolCallData toolCallData(final AgentContext context) {
				return ToolCallData.required(List.of(weatherTool));
			}

			@Override
			public AgentResponseHandler responseHandler(final AgentContext context) {
				return (completion, agentContext) -> true;
			}

			@Override
			public AgentNext chooseNextAgent(final AgentContext context) {
				return new AgentNext("ToolResultResponder", null);
			}
		};
		final var toolResultResponder = new Agent("ToolResultResponder",
				AgentType.CONVERSATION,
				ModelCategory.MEDIUM,
				null,
				null,
				"Answer using the tool result already present in the conversation.",
				null,
				new TextAgentResponseHandler("answer"),
				context -> {
					final var assistantTurn = context.getConversation().getConversation().getLast();
					context.getConversation().addTurn(new Conversation.Turn(Conversation.TurnType.TOOL, ToolParameters.create().add("forecast", "Berlin is rainy and 21C").json(), null, assistantTurn.toolCalls().getFirst().id()));
				},
				null,
				null,
				AgentNext::end);
		final var context = new AgentContext(Map.of(
				toolCaller.name(), toolCaller,
				toolResultResponder.name(), toolResultResponder));

		try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
			final var firstResult = toolCaller.execute(client, executorService, context).getFirst();
			final var secondResult = toolResultResponder.execute(client, executorService, firstResult).getFirst();

			assertEquals(2, requestCounter.get());
			assertEquals(ToolCallData.required(List.of(weatherTool)), firstRequestToolCallData.get());
			assertEquals(ToolCallData.auto(List.of(weatherTool)), secondRequestToolCallData.get());
			assertEquals("Berlin is rainy and 21C", secondResult.getStringValue("answer"));
		}
	}
}