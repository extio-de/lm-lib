package de.extio.lmlib.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.PropertySource;

import de.extio.lmlib.agent.responsehandler.JsonAgentResponseHandler;
import de.extio.lmlib.agent.responsehandler.TextAgentResponseHandler;
import de.extio.lmlib.agent.responsehandler.ToolCallingAgentResponseHandler;
import de.extio.lmlib.client.ClientService;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.ToolCall;
import de.extio.lmlib.client.ToolCallData;
import de.extio.lmlib.client.ToolDefinition;
import de.extio.lmlib.client.ToolParameters;
import de.extio.lmlib.grader.Grader2;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfileService;

/**
 * These tests have been verified with Llama 3.1 8B, Gemma2 27B, Gemma3 27B, GPT-OSS 20B, and Qwen3.5 9B models
 */
@Disabled("This test requires a running Llama server or a cloud subscription")
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@EnableAutoConfiguration
@PropertySource("classpath:/application-test.properties")
public class AgentTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AgentTest.class);
	
	@Autowired
	private AgentExecutorService agentExecutor;
	
	@Autowired
	private ClientService clientService;

	@Autowired
	private ModelProfileService modelProfileService;
	
	@Test
	void agenticFlow() throws IOException {
		final var agents = Map.of(
				"CodeSummarizer",
				new Agent("CodeSummarizer",
						AgentType.START_CONVERSATION,
						ModelCategory.MEDIUM,
						null,
						"Generate a summary of the following Java source code.",
						"The source code is:\n{{code}}",
						null,
						new TextAgentResponseHandler("summary"),
						null,
						null,
						null,
						context -> new AgentNext("FeatureIdentifier", null)),
				
				"FeatureIdentifier",
				new Agent("FeatureIdentifier",
						AgentType.CONVERSATION,
						ModelCategory.MEDIUM,
						null,
						"",
						"Generate a list of 5 distinct functional features. In scope is business logic, not code mechanics. Provide no preamble and no explanation. Return the response in JSON format with the following field: { \"features\": [\"Feature 1\", \"Feature 2\", ... ] }",
						null,
						new JsonAgentResponseHandler(),
						null,
						context -> context.getStringValues("features").forEach(feature -> LOGGER.info(feature)),
						null,
						context -> new AgentNext("FeatureAnalyzer", null)),
				
				"FeatureAnalyzer",
				new Agent("FeatureAnalyzer",
						AgentType.COMPLETION,
						ModelCategory.MEDIUM,
						null,
						"Analyze a feature of the following Java source code and describe the functionality and purpose of this feature. Only describe the implementation, don't suggest any possible improvements.",
						"""
								The source code is:
								{{code}}
								The feature is: [[features]]""",
						null,
						new TextAgentResponseHandler("featureDescription"),
						null,
						null,
						contexts -> BaseAgent.mergeContexts(List.of("features", "featureDescription"), contexts),
						context -> new AgentNext("AnswerGenerator", null)),
				
				"AnswerGenerator",
				new Agent("AnswerGenerator",
						AgentType.COMPLETION,
						ModelCategory.MEDIUM,
						null,
						"Generate a clear and concise blog post for an announcement from the following code analysis.",
						"""
								Write a concise description of the purpose in the blog post first and then concise information for the following topics: {{topics}}
								The code analysis:
								{{summary}}
								{{featureDescription}}""",
						null,
						new TextAgentResponseHandler("blogPost"),
						context -> {
							context.setStringValues("topics", List.of(String.join(", ", context.getStringValues("features").stream().toArray(size -> new String[size]))));
						},
						null,
						null,
						AgentNext::end));
		
		final var context = new AgentContext(agents);
		context.getContext().put("code", List.of(Files.readString(Path.of("src/main/java/de/extio/lmlib/agent/BaseAgent.java"))));
		
		final var resultContexts = this.agentExecutor.walk(agents.get("CodeSummarizer"), context);
		
		final var resultContext = resultContexts.getFirst();
		LOGGER.info(resultContext.getContext().get("blogPost").toString() + "\n\n" + resultContext.getGraph().toString() + "\n" + resultContext.getRequestStatistic().toString());
		
		assertEquals(1, resultContexts.size());
		assertEquals(5, resultContext.getContext().get("features").size());
		assertEquals(1, resultContext.getContext().get("blogPost").size());
		assertEquals(8, resultContext.getRequestStatistic().getRequests().get());
		for (final var feature : resultContext.getContext().get("features")) {
			assertTrue(Grader2.assessScoreBinary("Does the following blog post mention the feature " + feature, resultContext.getStringValue("blogPost"), ModelCategory.MEDIUM, this.modelProfileService, this.clientService));
		}
	}

	@Test
	void streamedAgenticFlow() throws IOException {
		final var lastStreamedSummary = new AtomicReference<String>();
		final var agents = Map.of(
				"CodeSummarizer",
				new Agent("CodeSummarizer",
						AgentType.START_CONVERSATION,
						ModelCategory.MEDIUM,
						null,
						"Generate a summary of the following Java source code.",
						"The source code is:\n{{code}}",
						null,
						new TextAgentResponseHandler("summary",
								null,
								null,
								null,
								(context, chunk) -> {
									if (chunk.content() != null) { // Since chunk can contain both content and reasoning content, we check for null
										context.setStringValue("summary_chunk", chunk.content());
									}
									if (chunk.reasoningContent() != null) {
										// System.out.print(chunk.reasoningContent()); // The reasoning can also be accessed if available
									}
									lastStreamedSummary.set(context.getStringValue("summary")); // The current key value can also be accessed (e.g. concatenated, depends on the StreamedAgentResponseHandler implementation)
								}
						),
						null,
						context -> {
							context.setStringValue("summary", context.getStringValue("summary") + "\npostprocessed");
						},
						null,
						AgentNext::end));
		
		final var context = new AgentContext(agents);
		context.setStreaming(true);
		context.getContext().put("code", List.of(Files.readString(Path.of("src/main/java/de/extio/lmlib/agent/BaseAgent.java"))));
		
		final var resultContexts = this.agentExecutor.walk(agents.get("CodeSummarizer"), context, contextUpdate -> {
			// This is an optional consumer that can be used to receive updates from the agent context
			System.out.print(contextUpdate.getStringValue("summary_chunk"));
		});
		
		assertEquals(1, resultContexts.size());
		assertEquals(lastStreamedSummary.get() + "\npostprocessed", resultContexts.getFirst().getStringValue("summary"));
		assertTrue(resultContexts.getFirst().getStringValue("summary").endsWith("postprocessed"));
		
		System.out.println();
		LOGGER.info("### final result");
		LOGGER.info(resultContexts.getFirst().toString());
	}

	@Test
	void toolCallingAgenticFlow() {
		final var weatherTool = new ToolDefinition(
				"get_weather",
				"Gets the current weather for a given location.",
				ToolParameters.create(Map.of("location", "City and country, for example Berlin, Germany")),
				true);
		final var trafficTool = new ToolDefinition(
				"get_traffic",
				"Gets the current traffic for a given location.",
				ToolParameters.create(Map.of("location", "City and country, for example Berlin, Germany")),
				true);
		final var agents = Map.of(
				"ToolCaller",
				new Agent("ToolCaller",
						AgentType.START_CONVERSATION,
						ModelCategory.MEDIUM,
						null,
						"You must call both provided tools in the same turn for this request. First call get_weather and get_traffic for Berlin, Germany. After both tool results are available, answer in one sentence that explicitly includes the exact facts from both tool outputs. Do not answer before both tool calls have been made.",
						"What is the weather and how is the traffic in Berlin, Germany? Call both tools in the same turn.",
						List.of(weatherTool, trafficTool),
						new ToolCallingAgentResponseHandler(
								new TextAgentResponseHandler("answer"),
								(completion, agentContext, toolCallResults) -> {
									if (completion.toolCalls().isEmpty()) {
										return false;
									}
									agentContext.setStringValues("toolNames", completion.toolCalls().stream().map(ToolCall::name).toList());
									agentContext.setStringValues("toolArguments", completion.toolCalls().stream().map(toolCall -> toolCall.arguments() == null ? "" : toolCall.arguments()).toList());
									var handledToolCall = false;
									for (final var toolCall : completion.toolCalls()) {
										if ("get_weather".equals(toolCall.name())) {
											toolCallResults.add(toolCall, ToolParameters.create().add("forecast", "Berlin is rainy and 21C"));
											handledToolCall = true;
										}
										else if ("get_traffic".equals(toolCall.name())) {
											toolCallResults.add(toolCall, ToolParameters.create().add("traffic", "Berlin traffic is congested"));
											handledToolCall = true;
										}
									}
									return handledToolCall;
								}),
						context -> context.setValue("_toolCallData", ToolCallData.required(List.of(weatherTool, trafficTool)).withParallelToolCalls(true)),
						null,
						null,
						AgentNext::end));

		final var context = new AgentContext(agents);
		assertTrue(agents.get("ToolCaller").supportsToolCalling(context, this.clientService));

		final var resultContexts = this.agentExecutor.walk(agents.get("ToolCaller"), context);
		final var resultContext = resultContexts.getFirst();
		final var answer = resultContext.getStringValue("answer");

		assertEquals(1, resultContexts.size());
		assertTrue(this.hasToolCall(resultContext, "get_weather", "Berlin"));
		assertTrue(this.hasToolCall(resultContext, "get_traffic", "Berlin"));
		assertTrue(Grader2.assessScoreBinary("Does the text say that Berlin is rainy and 21C?", answer, ModelCategory.MEDIUM, this.modelProfileService, this.clientService));
		assertTrue(Grader2.assessScoreBinary("Does the text say that traffic in Berlin is congested?", answer, ModelCategory.MEDIUM, this.modelProfileService, this.clientService));
	}

	@Test
	void toolCallsCanBeHandledByNextAgentConversation() {
		final var weatherTool = new ToolDefinition(
				"get_weather",
				"Gets the current weather for a given location.",
				ToolParameters.create(Map.of("location", "City and country, for example Berlin, Germany")),
				true);
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
				return "You must call the provided tool for weather questions. Do not answer before the tool has been called.";
			}

			@Override
			public String textTemplate() {
				return "What is the weather in Berlin, Germany? Use the tool.";
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
			public boolean supportsToolCalling(final AgentContext context) {
				return true;
			}

			@Override
			public AgentNext chooseNextAgent(final AgentContext context) {
				return new AgentNext("ToolResultResponder", null);
			}

			@Override
			public de.extio.lmlib.agent.responsehandler.AgentResponseHandler responseHandler(final AgentContext context) {
				return (completion, agentContext) -> true;
			}
		};
		final BaseAgent toolResultResponder = new Agent("ToolResultResponder",
				AgentType.CONVERSATION,
				ModelCategory.MEDIUM,
				null,
				"Use the existing tool result in the conversation to answer the original weather question.",
				"Answer the original question using the tool result already present in the conversation. Do not call any tool.",
				null,
				new TextAgentResponseHandler("answer"),
				context -> {
					final var conversation = context.getConversation();
					final var assistantTurn = conversation.getConversation().getLast();
					context.setStringValue("handoffTurnType", assistantTurn.type().name());
					context.setStringValue("handoffToolName", assistantTurn.toolCalls().getFirst().name());
					context.setStringValue("handoffToolArguments", assistantTurn.toolCalls().getFirst().arguments());
					context.setStringValue("handoffToolCallId", assistantTurn.toolCalls().getFirst().id());
					conversation.addTurn(new Conversation.Turn(Conversation.TurnType.TOOL, ToolParameters.create().add("forecast", "Berlin is rainy and 21C").json(), null, assistantTurn.toolCalls().getFirst().id()));
				},
				null,
				null,
				AgentNext::end);
		final var agents = Map.of(
				"ToolCaller", toolCaller,
				"ToolResultResponder", toolResultResponder);

		final var context = new AgentContext(agents);
		final var resultContexts = this.agentExecutor.walk(toolCaller, context);
		final var resultContext = resultContexts.getFirst();

		assertEquals(1, resultContexts.size());
		assertEquals("ASSISTANT", resultContext.getStringValue("handoffTurnType"));
		assertEquals("get_weather", resultContext.getStringValue("handoffToolName"));
		assertTrue(resultContext.getStringValue("handoffToolArguments").contains("Berlin"));
		assertTrue(resultContext.getConversation().getConversation().stream().anyMatch(turn -> turn.type() == Conversation.TurnType.TOOL
				&& resultContext.getStringValue("handoffToolCallId").equals(turn.toolCallId())
				&& turn.text().contains("Berlin is rainy and 21C")));
		assertTrue(Grader2.assessScoreBinary("Does the text say that Berlin is rainy and 21C?", resultContext.getStringValue("answer"), ModelCategory.MEDIUM, this.modelProfileService, this.clientService));
	}
	
	@Test
	void doubleBranch() {
		final var agents = Map.of(
				"FirstLevel",
				new Agent("FirstLevel",
						AgentType.COMPLETION,
						ModelCategory.MEDIUM,
						null,
						"",
						"Generate 5 random numbers from 1 to 10. Provide no preamble and no explanation. Return the response in JSON format with the following field: { \"numbers\": [\"Number 1\", \"Number 2\", ... ] }",
						null,
						new JsonAgentResponseHandler(),
						null,
						null,
						null,
						context -> new AgentNext("SecondLevel", null)),
				
				"SecondLevel",
				new Agent("SecondLevel",
						AgentType.COMPLETION,
						ModelCategory.MEDIUM,
						null,
						"",
						"Generate 5 random numbers from 1 to 10. The previous number was [[numbers]]. Provide no preamble and no explanation. Return the response in JSON format with the following field: { \"random\": [\"Number 1\", \"Number 2\", ... ] }",
						null,
						new JsonAgentResponseHandler(),
						null,
						null,
						null,
						context -> new AgentNext("Calculator", null)),
				
				"Calculator",
				new Agent("Calculator",
						AgentType.COMPLETION,
						ModelCategory.MEDIUM,
						null,
						"",
						"Add these 2 numbers: {{numbers}} + [[random]] ; Provide no preamble and no explanation. Return the response in JSON format with the following field: { \"result\": \"result\" }",
						null,
						new JsonAgentResponseHandler(),
						null,
						null,
						null,
						context -> new AgentNext("Grader", null)),
				
				"Grader",
				new Agent("Grader",
						AgentType.COMPLETION,
						ModelCategory.MEDIUM,
						null,
						"You are a helpful assistant.",
						"If you add {{numbers}} marbles to {{random}} marbles, do you get {{result}} marbles as the result? Answer with 'yes' or 'no'",
						null,
						new TextAgentResponseHandler("grade"),
						null,
						null,
						null,
						context -> {
							final boolean pass = context.getContext().get("grade").getFirst().toString().strip().toLowerCase().equals("yes");
							if (!pass) {
								try {
									Integer.parseInt(context.getContext().get("numbers").getFirst().toString().strip());
									Integer.parseInt(context.getContext().get("random").getFirst().toString().strip());
								}
								catch (final NumberFormatException exc) {
									context.getContext().put("numbers", List.of(String.valueOf(ThreadLocalRandom.current().nextInt(10))));
									context.getContext().put("random", List.of(String.valueOf(ThreadLocalRandom.current().nextInt(10))));
								}
							}
							return new AgentNext(pass ? null : "Calculator", pass);
						}));
		
		final var context = new AgentContext(agents);
		
		final var resultContexts = this.agentExecutor.walk(agents.get("FirstLevel"), context);
		
		assertEquals(25, resultContexts.size());
		assertTrue(resultContexts.getFirst().getRequestStatistic().getRequests().get() >= 56);
		for (final var resultContext : resultContexts) {
			final int number0 = Integer.parseInt(resultContext.getContext().get("numbers").getFirst().toString().strip());
			final int number1 = Integer.parseInt(resultContext.getContext().get("random").getFirst().toString().strip());
			final int expected = number0 + number1;
			assertEquals(expected, Integer.parseInt(resultContext.getContext().get("result").getFirst().toString().strip()));
			LOGGER.info(number0 + " + " + number1 + " = " + expected + "; " + resultContext.getGraph().toString() + " " + resultContext.getRequestStatistic().toString());
		}
	}

	private boolean hasToolCall(final AgentContext context, final String toolName, final String requiredArgumentPart) {
		final var toolNames = context.getStringValues("toolNames");
		final var toolArguments = context.getStringValues("toolArguments");
		if (toolNames == null || toolArguments == null || toolNames.size() != toolArguments.size()) {
			return false;
		}
		for (int i = 0; i < toolNames.size(); i++) {
			if (toolName.equals(toolNames.get(i)) && toolArguments.get(i) != null && toolArguments.get(i).contains(requiredArgumentPart)) {
				return true;
			}
		}
		return false;
	}
	
}
