package de.extio.lmlib.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;

import de.extio.lmlib.agent.responsehandler.JsonAgentResponseHandler;
import de.extio.lmlib.agent.responsehandler.TextAgentResponseHandler;
import de.extio.lmlib.client.ClientService;
import de.extio.lmlib.grader.Grader;
import de.extio.lmlib.profile.ModelCategory;
import io.netty.util.internal.ThreadLocalRandom;

/**
 * These tests have been verified with Llama 3.1 8B and Gemma2 27B models
 */
@Disabled("This test requires a running Llama server or an Azure subscription (setup azure key in model profile)")
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@ComponentScan(basePackages = "de.extio.lmlib")
@PropertySource("classpath:/application-test.properties")
public class AgentTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AgentTest.class);
	
	@Autowired
	private AgentExecutorService agentExecutor;
	
	@Autowired
	private ClientService clientService;
	
	@Test
	void agenticFlow() throws IOException {
		final var agents = Map.of(
				"CodeSummarizer",
				new Agent("CodeSummarizer",
						AgentType.START_CONVERSATION,
						ModelCategory.MEDIUM,
						"Generate a summary of the following Java source code.",
						"The source code is:\n{{code}}",
						new TextAgentResponseHandler("summary"),
						null,
						null,
						null,
						context -> new AgentNext("FeatureIdentifier", null)),
				
				"FeatureIdentifier",
				new Agent("FeatureIdentifier",
						AgentType.CONVERSATION,
						ModelCategory.MEDIUM,
						"",
						"Generate a list of 5 distinct functional features. In scope is business logic, not code mechanics. Provide no preamble and no explanation. Return the response in JSON format with the following field: { \"features\": [\"Feature 1\", \"Feature 2\", ... ] }",
						new JsonAgentResponseHandler(),
						null,
						context -> context.getStringValues("features").forEach(feature -> LOGGER.info(feature)),
						null,
						context -> new AgentNext("FeatureAnalyzer", null)),
				
				"FeatureAnalyzer",
				new Agent("FeatureAnalyzer",
						AgentType.COMPLETION,
						ModelCategory.MEDIUM,
						"Analyze a feature of the following Java source code and describe the functionality and purpose of this feature. Only describe the implementation, don't suggest any possible improvements.",
						"""
								The source code is:
								{{code}}
								The feature is: [[features]]""",
						new TextAgentResponseHandler("featureDescription"),
						null,
						null,
						contexts -> BaseAgent.mergeContexts(List.of("features", "featureDescription"), contexts),
						context -> new AgentNext("AnswerGenerator", null)),
				
				"AnswerGenerator",
				new Agent("AnswerGenerator",
						AgentType.COMPLETION,
						ModelCategory.MEDIUM,
						"Generate a clear and concise blog post for an announcement from the following code analysis.",
						"""
								Write a concise description of the purpose in the blog post first and then concise information for the following topics: {{topics}}
								The code analysis:
								{{summary}}
								{{featureDescription}}""",
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
			assertTrue(Grader.assessScoreBinary("Does the following blog post mention the feature " + feature, resultContext.getStringValue("blogPost"), this.clientService));
		}
	}

	@Test
	void streamedAgenticFlow() throws IOException {
		final var agents = Map.of(
				"CodeSummarizer",
				new Agent("CodeSummarizer",
						AgentType.START_CONVERSATION,
						ModelCategory.MEDIUM,
						"Generate a summary of the following Java source code.",
						"The source code is:\n{{code}}",
						new TextAgentResponseHandler("summary"),
						null,
						context -> {
							context.setStringValue("summary", context.getStringValue("summary") + "\npostprocessed");
						},
						null,
						AgentNext::end));
		
		final var context = new AgentContext(agents);
		context.setStreaming(true);
		context.getContext().put("code", List.of(Files.readString(Path.of("src/main/java/de/extio/lmlib/agent/BaseAgent.java"))));
		
		final var lastStreamedSummary = new AtomicReference<String>();
		final var resultContexts = this.agentExecutor.walk(agents.get("CodeSummarizer"), context, contextUpdate -> {
			if ("summary".equals(contextUpdate.getStringValue(TextAgentResponseHandler.UPDATE_KEY))) { // UPDATE_KEY helps you to identify the streamed chunk
				System.out.print(contextUpdate.getStringValue("summary_chunk")); // Single chunks can be accessed via _chunk suffix, depends on the StreamedAgentResponseHandler implementation
				lastStreamedSummary.set(contextUpdate.getStringValue("summary")); // The current key value can also be accessed (e.g. concatenated, depends on the StreamedAgentResponseHandler implementation)
			}
		});
		
		assertEquals(1, resultContexts.size());
		assertEquals(lastStreamedSummary.get(), resultContexts.getFirst().getStringValue("summary"));
		assertTrue(resultContexts.getFirst().getStringValue("summary").endsWith("postprocessed"));
		
		System.out.println();
		LOGGER.info("### final result");
		LOGGER.info(resultContexts.getFirst().toString());
	}
	
	@Test
	void doubleBranch() {
		final var agents = Map.of(
				"FirstLevel",
				new Agent("FirstLevel",
						AgentType.COMPLETION,
						ModelCategory.MEDIUM,
						"",
						"Generate 5 random numbers from 1 to 10. Provide no preamble and no explanation. Return the response in JSON format with the following field: { \"numbers\": [\"Number 1\", \"Number 2\", ... ] }",
						new JsonAgentResponseHandler(),
						null,
						null,
						null,
						context -> new AgentNext("SecondLevel", null)),
				
				"SecondLevel",
				new Agent("SecondLevel",
						AgentType.COMPLETION,
						ModelCategory.MEDIUM,
						"",
						"Generate 5 random numbers from 1 to 10. The previous number was [[numbers]]. Provide no preamble and no explanation. Return the response in JSON format with the following field: { \"random\": [\"Number 1\", \"Number 2\", ... ] }",
						new JsonAgentResponseHandler(),
						null,
						null,
						null,
						context -> new AgentNext("Calculator", null)),
				
				"Calculator",
				new Agent("Calculator",
						AgentType.COMPLETION,
						ModelCategory.MEDIUM,
						"",
						"Add these 2 numbers: {{numbers}} + [[random]] ; Provide no preamble and no explanation. Return the response in JSON format with the following field: { \"result\": \"result\" }",
						new JsonAgentResponseHandler(),
						null,
						null,
						null,
						context -> new AgentNext("Grader", null)),
				
				"Grader",
				new Agent("Grader",
						AgentType.COMPLETION,
						ModelCategory.MEDIUM,
						"You are a helpful assistant.",
						"If you add {{numbers}} marbles to {{random}} marbles, do you get {{result}} marbles as the result? Answer with 'yes' or 'no'",
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
	
}
