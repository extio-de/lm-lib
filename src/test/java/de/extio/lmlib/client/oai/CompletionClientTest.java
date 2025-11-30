package de.extio.lmlib.client.oai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.Conversation.TurnType;
import de.extio.lmlib.client.oai.completion.chat.ChatCompletionClient;
import de.extio.lmlib.client.oai.completion.text.TextCompletionClient;
import de.extio.lmlib.grader.Grader;
import de.extio.lmlib.profile.ModelCategory;

// @Disabled("This test requires a running Llama server")
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@ComponentScan(basePackages = "de.extio.lmlib")
@PropertySource("classpath:/application-test.properties")
public class CompletionClientTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CompletionClientTest.class);
	
	@Autowired
	private TextCompletionClient textCompletionClient;
	
	@Autowired
	private ChatCompletionClient chatCompletionclient;
	
	@Test
	void completion() throws Exception {
		final var completion = this.textCompletionClient.completion(
				ModelCategory.SMALL,
				"You are a helpful assistant",
				"Say the color is green");
		
		LOGGER.info(completion.reasoning());
		LOGGER.info(completion.response());
		
		assertTrue(Grader.assessScoreBinary("Does the text mention the color green?", completion.response(), this.chatCompletionclient));
	}
	
	@Test
	void chatTextCompletion() throws Exception {
		final var conversation = Conversation.create("You are a helpful assistant", "I would like to paint my canvas in a solid color.");
		conversation.addTurn(new Turn(TurnType.ASSISTANT, "Which color do you want to see?"));
		conversation.addTurn(new Turn(TurnType.USER, "You choose!"));
		final var completion = this.textCompletionClient.conversation(ModelCategory.SMALL, conversation);
		
		LOGGER.info(completion.reasoning());
		LOGGER.info(completion.response());
		
		assertTrue(Grader.assessScoreBinary("Does the text mention a color?", completion.response(), this.chatCompletionclient));
	}
	
	@Test
	void streamTextCompletion() throws Exception {
		final var conversation = Conversation.create("You are a helpful assistant", "I would like to play a role-playing game with you.");
		conversation.addTurn(new Turn(TurnType.ASSISTANT, "Which character do you choose?"));
		conversation.addTurn(new Turn(TurnType.USER, "You choose! But I need you to explain the character attributes to me."));
		final StringBuilder sb = new StringBuilder();
		final var completion = this.textCompletionClient.streamConversation(ModelCategory.SMALL, conversation, chunk -> {
			if (chunk.reasoningContent() != null) {
				System.out.print(chunk.reasoningContent());
			}
			if (chunk.content() != null) {
				System.out.print(chunk.content());
				sb.append(chunk.content());
			}
		});
		
		if (completion.reasoning() != null) {
			// Prompt template does not separate reasoning and response from the stream at the moment
			assertTrue(sb.toString().contains(completion.reasoning()));
			assertTrue(sb.toString().contains(completion.response()));
			assertFalse(completion.response().contains(completion.reasoning()));
		}
		else {
			assertEquals(completion.response(), sb.toString());
		}
		assertTrue(Grader.assessScoreBinary("Does the text mention a role-playing game character?", completion.response(), this.chatCompletionclient));
	}
	
	@Test
	void chatCompletion() throws Exception {
		final var conversation = Conversation.create("You are a helpful assistant", "I would like to paint my canvas in a solid color.");
		conversation.addTurn(new Turn(TurnType.ASSISTANT, "Which color do you want to see?"));
		conversation.addTurn(new Turn(TurnType.USER, "You choose!"));
		final var completion = this.chatCompletionclient.conversation(ModelCategory.MEDIUM, conversation);
		
		LOGGER.info(completion.reasoning());
		LOGGER.info(completion.response());
		
		assertTrue(Grader.assessScoreBinary("Does the text mention a color?", completion.response(), this.chatCompletionclient));
	}
	
	@Test
	void streamChatCompletion() throws Exception {
		final var conversation = Conversation.create("You are a helpful assistant", "I would like to play a game of chess.");
		conversation.addTurn(new Turn(TurnType.ASSISTANT, "Which opening do you choose?"));
		conversation.addTurn(new Turn(TurnType.USER, "You choose! But I need you to explain the opening to me."));
		final StringBuilder sb = new StringBuilder();
		final var completion = this.chatCompletionclient.streamConversation(ModelCategory.MEDIUM, conversation, chunk -> {
			if (chunk.reasoningContent() != null) {
				System.out.print(chunk.reasoningContent());
			}
			if (chunk.content() != null) {
				System.out.print(chunk.content());
				sb.append(chunk.content());
			}
		});
		
		assertEquals(completion.response(), sb.toString());
		assertTrue(Grader.assessScoreBinary("Does the text explain a chess opening?", completion.response(), this.chatCompletionclient));
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
		
		final var completion = this.textCompletionClient.completion(
				null,
				"You are counting machine",
				"How many numbers do you count?\n" + sb.toString());
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
				final var completion = this.textCompletionClient.completion(
						null,
						"You are a helpful assistant",
						"How do I calculate the annual profit margin?");
				LOGGER.info(completion.response());
			}
			catch (final Exception e) {
				LOGGER.error("Exception", e);
			}
		}, () -> {
			try {
				final var completion = this.textCompletionClient.completion(
						null,
						"You are a helpful assistant",
						"How do I calculate the operating profit ratio?");
				LOGGER.info(completion.response());
			}
			catch (final Exception e) {
				LOGGER.error("Exception", e);
			}
		}, () -> {
			try {
				final var completion = this.textCompletionClient.completion(
						null,
						"You are a helpful assistant",
						"How do I calculate the return of investment?");
				LOGGER.info(completion.response());
			}
			catch (final Exception e) {
				LOGGER.error("Exception", e);
			}
		}, () -> {
			try {
				final var completion = this.textCompletionClient.completion(
						null,
						"You are a helpful assistant",
						"How do I calculate the return on net worth?");
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
