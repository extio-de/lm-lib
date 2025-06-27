package de.extio.lmlib.client.oai;

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
import de.extio.lmlib.client.oai.textcompletion.TextCompletionClient;
import de.extio.lmlib.profile.ModelCategory;

//@Disabled("This test requires a running Llama server")
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@ComponentScan(basePackages = "de.extio.lmlib")
@PropertySource("classpath:/application-test.properties")
public class TextCompletionClientTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TextCompletionClientTest.class);
	
	@Autowired
	private TextCompletionClient client;
	
	@Test
	void completion() throws Exception {
		final var completion = this.client.completion(
				null,
				"You are a helpful assistant",
				"",
				"Say the color is green");
		
		LOGGER.info(completion.response());
		
		assertTrue(this.assessScoreBinary("Does the text mention the color green?", completion.response()));
	}
	
	@Test
	void chat() throws Exception {
		final var conversation = Conversation.create("You are a helpful assistant", "I would like to paint my canvas in a solid color.");
		conversation.addTurn(new Turn(TurnType.ASSISTANT, "Which color do you want to see?"));
		conversation.addTurn(new Turn(TurnType.USER, "You choose!"));
		final var completion = this.client.conversation(ModelCategory.MEDIUM, conversation);
		
		LOGGER.info(completion.response());
		
		assertTrue(this.assessScoreBinary("Does the text mention a color?", completion.response()));
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
		
		final var completion = this.client.completion(
				null,
				"You are counting machine",
				"How many numbers do you count?",
				sb.toString());
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
				final var completion = this.client.completion(
						null,
						"You are a helpful assistant",
						"",
						"How do I calculate the annual profit margin?");
				LOGGER.info(completion.response());
			}
			catch (final Exception e) {
				LOGGER.error("Exception", e);
			}
		}, () -> {
			try {
				final var completion = this.client.completion(
						null,
						"You are a helpful assistant",
						"",
						"How do I calculate the operating profit ratio?");
				LOGGER.info(completion.response());
			}
			catch (final Exception e) {
				LOGGER.error("Exception", e);
			}
		}, () -> {
			try {
				final var completion = this.client.completion(
						null,
						"You are a helpful assistant",
						"",
						"How do I calculate the return of investment?");
				LOGGER.info(completion.response());
			}
			catch (final Exception e) {
				LOGGER.error("Exception", e);
			}
		}, () -> {
			try {
				final var completion = this.client.completion(
						null,
						"You are a helpful assistant",
						"",
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
	
	private boolean assessScoreBinary(final String question, final String text) {
		LOGGER.info(question);
		
		var score = 0;
		while (Math.abs(score) < 3) {
			final var completion = this.client.completion(
					ModelCategory.MEDIUM,
					"""
							You are a grader assessing the truthfulness of a given text to a user question.
							Please provide a binary response 'true' or 'false' for the following text.
							'true' means that the text provides a truthful answer to the question, while 'false' means that it does not.
							Only provide the response as a single word and no preamble and no explanation.""",
					"Here is the text:\n" + text,
					"\nHere is the user question: " + question);
			LOGGER.info(completion.response());
			score += Boolean.parseBoolean(completion.response().strip()) ? 1 : -1;
			if (Math.abs(score) == 2) {
				break;
			}
		}
		
		final var result = score > 0;
		LOGGER.info("->" + String.valueOf(result));
		return result;
	}
}
