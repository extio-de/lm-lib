package de.extio.lmlib.client.oai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

import de.extio.lmlib.client.ClientService;
import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.Conversation.TurnType;
import de.extio.lmlib.client.ToolCallData;
import de.extio.lmlib.client.ToolDefinition;
import de.extio.lmlib.client.ToolParameters;
import de.extio.lmlib.client.oai.completion.chat.ChatCompletionClient;
import de.extio.lmlib.client.oai.completion.text.TextCompletionClient;
import de.extio.lmlib.grader.Grader2;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfileService;

@Disabled("This test requires a running Llama server")
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@EnableAutoConfiguration
@PropertySource("classpath:/application-test.properties")
public class CompletionClientTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CompletionClientTest.class);
	
	@Autowired
	private TextCompletionClient textCompletionClient;
	
	@Autowired
	private ChatCompletionClient chatCompletionclient;
	
	@Autowired
	private ModelProfileService modelProfileService;
	
	@Autowired
	private ClientService clientService;
	
	@Test
	void completion() throws Exception {
		final var modelProfile = this.modelProfileService.getModelProfile("profile.model.textcompletion");
		final var completion = this.textCompletionClient.conversation(
				modelProfile,
				Conversation.create("You are a helpful assistant", "Say the color is green"),
				null,
				false);
		
		LOGGER.info(completion.reasoning());
		LOGGER.info(completion.response());
		
		assertTrue(Grader2.assessScoreBinary("Does the text mention the color green?", completion.response(), modelProfile, this.clientService));
	}
	
	@Test
	void chatTextCompletion() throws Exception {
		final var modelProfile = this.modelProfileService.getModelProfile("profile.model.textcompletion");
		final var conversation = Conversation.create("You are a helpful assistant", "I would like to paint my canvas in a solid color.");
		conversation.addTurn(new Turn(TurnType.ASSISTANT, "Which color do you want to see?"));
		conversation.addTurn(new Turn(TurnType.USER, "You choose!"));
		final var completion = this.textCompletionClient.conversation(modelProfile, conversation, null, false);
		
		LOGGER.info(completion.reasoning());
		LOGGER.info(completion.response());
		
		assertTrue(Grader2.assessScoreBinary("Does the text mention a color?", completion.response(), modelProfile, this.clientService));
	}
	
	@Test
	void streamTextCompletion() throws Exception {
		final var modelProfile = this.modelProfileService.getModelProfile("profile.model.textcompletion");
		final var conversation = Conversation.create("You are a helpful assistant", "I would like to play a role-playing game with you.");
		conversation.addTurn(new Turn(TurnType.ASSISTANT, "Which character do you choose?"));
		conversation.addTurn(new Turn(TurnType.USER, "You choose! But I need you to explain the character attributes to me."));
		final StringBuilder sb = new StringBuilder();
		final var completion = this.textCompletionClient.streamConversation(modelProfile, conversation, chunk -> {
			if (chunk.reasoningContent() != null) {
				System.out.print(chunk.reasoningContent());
			}
			if (chunk.content() != null) {
				System.out.print(chunk.content());
				sb.append(chunk.content());
			}
		}, null, false);
		
		if (completion.reasoning() != null) {
			// Prompt template does not separate reasoning and response from the stream at the moment
			assertTrue(sb.toString().contains(completion.reasoning()));
			assertTrue(sb.toString().contains(completion.response()));
			assertFalse(completion.response().contains(completion.reasoning()));
		}
		else {
			assertEquals(completion.response(), sb.toString());
		}
		assertTrue(Grader2.assessScoreBinary("Does the text mention a role-playing game character?", completion.response(), modelProfile, this.clientService));
	}
	
	@Test
	void chatCompletion() throws Exception {
		final var modelProfile = this.modelProfileService.getModelProfile("profile.model.chatcompletion");
		final var conversation = Conversation.create("You are a helpful assistant", "I would like to paint my canvas in a solid color.");
		conversation.addTurn(new Turn(TurnType.ASSISTANT, "Which color do you want to see?"));
		conversation.addTurn(new Turn(TurnType.USER, "You choose!"));
		final var completion = this.chatCompletionclient.conversation(modelProfile, conversation, null, false);
		
		LOGGER.info(completion.reasoning());
		LOGGER.info(completion.response());
		
		assertTrue(Grader2.assessScoreBinary("Does the text mention a color?", completion.response(), modelProfile, this.clientService));
	}
	
	@Test
	void streamChatCompletion() throws Exception {
		final var modelProfile = this.modelProfileService.getModelProfile("profile.model.chatcompletion");
		final var conversation = Conversation.create("You are a helpful assistant", "I would like to play a game of chess.");
		conversation.addTurn(new Turn(TurnType.ASSISTANT, "Which opening do you choose?"));
		conversation.addTurn(new Turn(TurnType.USER, "You choose! But I need you to explain the opening to me."));
		final StringBuilder sb = new StringBuilder();
		final var completion = this.chatCompletionclient.streamConversation(modelProfile, conversation, chunk -> {
			if (chunk.reasoningContent() != null) {
				System.out.print(chunk.reasoningContent());
			}
			if (chunk.content() != null) {
				System.out.print(chunk.content());
				sb.append(chunk.content());
			}
		}, null, false);
		
		assertEquals(completion.response(), sb.toString());
		assertTrue(Grader2.assessScoreBinary("Does the text explain a chess opening?", completion.response(), modelProfile, this.clientService));
	}
	
	@Test
	void toolCallingChatCompletion() throws Exception {
		final var modelProfile = this.modelProfileService.getModelProfile("profile.model.chatcompletion");
		final var toolDefinition = new ToolDefinition(
				"get_weather",
				"Gets the current weather for a given location.",
				ToolParameters.create(Map.of("location", "City and country, for example Berlin, Germany")),
				true);
		final var conversation = Conversation.create(
				"You are a helpful assistant. Use the provided tool when the user asks for weather information. After receiving tool results, answer using the forecast from the tool output.",
				"What is the weather in Berlin, Germany? Use the tool.");
		final var toolCallData = ToolCallData.required(List.of(toolDefinition));
		final var completion = this.chatCompletionclient.conversation(
				modelProfile,
				conversation,
				toolCallData,
				false);
		
		assertTrue(this.chatCompletionclient.supportsToolCalling());
		assertFalse(this.textCompletionClient.supportsToolCalling());
		assertTrue(this.clientService.supportsToolCalling(modelProfile));
		assertTrue(completion.finishReason() == CompletionFinishReason.TOOL_CALLS);
		assertFalse(completion.toolCalls().isEmpty());
		assertEquals("get_weather", completion.toolCalls().getFirst().name());
		assertTrue(completion.toolCalls().getFirst().arguments().contains("Berlin"));

		conversation.addTurn(new Turn(TurnType.ASSISTANT, "", completion.toolCalls(), null));
		conversation.addTurn(new Turn(TurnType.TOOL, ToolParameters.create().add("forecast", "Berlin is rainy and 21C").json(), null, completion.toolCalls().getFirst().id()));

		final var finalCompletion = this.chatCompletionclient.conversation(modelProfile, conversation, ToolCallData.auto(List.of(toolDefinition)), false);
		assertTrue(finalCompletion.finishReason() == CompletionFinishReason.DONE);
		assertTrue(Grader2.assessScoreBinary("Does the text say that Berlin is rainy and 21C?", finalCompletion.response(), modelProfile, this.clientService));
	}
	
	@Test
	void streamToolCallingChatCompletion() throws Exception {
		final var modelProfile = this.modelProfileService.getModelProfile("profile.model.chatcompletion");
		final var getWeather = new ToolDefinition(
				"get_weather",
				"Gets the current weather for a given location.",
				ToolParameters.create(Map.of("location", "City and country, for example Berlin, Germany")),
				true);
		final var getCpu = new ToolDefinition(
				"get_cpu",
				"Gets the current CPU utilization.",
				ToolParameters.create(Map.of("cpu", "CPU identifier, for example CPU0. Use CPU for overall utilization.")),
				true);
		final var completion = this.chatCompletionclient.streamConversation(
				modelProfile,
				Conversation.create(
						"You are a helpful assistant. Use the provided tool when applicable.",
						"How high is the current CPU utilization?"),
				chunk -> {
					if (chunk.reasoningContent() != null) {
						System.out.print(chunk.reasoningContent());
					}
					if (chunk.content() != null) {
						System.out.print(chunk.content());
					}
				},
				ToolCallData.required(List.of(getWeather, getCpu)),
				false);
		
		assertTrue(this.chatCompletionclient.supportsToolCalling());
		assertFalse(this.textCompletionClient.supportsToolCalling());
		assertTrue(this.clientService.supportsToolCalling(modelProfile));
		assertTrue(completion.finishReason() == CompletionFinishReason.TOOL_CALLS);
		assertFalse(completion.toolCalls().isEmpty());
		assertEquals("get_cpu", completion.toolCalls().getFirst().name());
		assertTrue(completion.toolCalls().getFirst().arguments().contains("CPU"));
	}
	
	@Disabled
	@Test
	void hugePrompt() throws Exception {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 10000; i++) {
			sb.append(i);
			sb.append(", ");
		}
		sb.delete(sb.length() - 2, sb.length());
		
		final var completion = this.textCompletionClient.conversation(
				ModelCategory.MEDIUM,
				Conversation.create("You are counting machine", "How many numbers do you count?\n" + sb.toString()),
				null,
				false);
		LOGGER.info(completion.response());
	}
	
	@Disabled
	@Test
	void parallelRequests() throws Exception {
		final LocalDateTime start = LocalDateTime.now();
		
		final Tasks tasks = this.createTasks();
		
		final ExecutorService executor = Executors.newFixedThreadPool(4);
		try {
			CompletableFuture.allOf(
					CompletableFuture.runAsync(tasks.task1, executor),
					CompletableFuture.runAsync(tasks.task2, executor),
					CompletableFuture.runAsync(tasks.task3, executor),
					CompletableFuture.runAsync(tasks.task4, executor))
					.join();
		}
		finally {
			executor.shutdown();
		}
		
		LOGGER.info("Duration: " + java.time.Duration.between(start, LocalDateTime.now()));
	}
	
	@Disabled
	@Test
	void serialRequests() throws Exception {
		final LocalDateTime start = LocalDateTime.now();
		
		final Tasks tasks = this.createTasks();
		
		tasks.task1.run();
		tasks.task2.run();
		tasks.task3.run();
		tasks.task4.run();
		
		LOGGER.info("Duration: " + java.time.Duration.between(start, LocalDateTime.now()));
	}
	
	private Tasks createTasks() {
		return new Tasks(() -> {
			try {
				final var completion = this.textCompletionClient.conversation(
						ModelCategory.MEDIUM,
						Conversation.create("You are a helpful assistant", "How do I calculate the annual profit margin?"),
						null,
						false);
				LOGGER.info(completion.response());
			}
			catch (final Exception e) {
				LOGGER.error("Exception", e);
			}
		}, () -> {
			try {
				final var completion = this.textCompletionClient.conversation(
						ModelCategory.MEDIUM,
						Conversation.create("You are a helpful assistant", "How do I calculate the operating profit ratio?"),
						null,
						false);
				LOGGER.info(completion.response());
			}
			catch (final Exception e) {
				LOGGER.error("Exception", e);
			}
		}, () -> {
			try {
				final var completion = this.textCompletionClient.conversation(
						ModelCategory.MEDIUM,
						Conversation.create("You are a helpful assistant", "How do I calculate the return of investment?"),
						null,
						false);
				LOGGER.info(completion.response());
			}
			catch (final Exception e) {
				LOGGER.error("Exception", e);
			}
		}, () -> {
			try {
				final var completion = this.textCompletionClient.conversation(
						ModelCategory.MEDIUM,
						Conversation.create("You are a helpful assistant", "How do I calculate the return on net worth?"),
						null,
						false);
				LOGGER.info(completion.response());
			}
			catch (final Exception e) {
				LOGGER.error("Exception", e);
			}
		});
	}
	
	static record Tasks(Runnable task1, Runnable task2, Runnable task3, Runnable task4) {
	}
}
