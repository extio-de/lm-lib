package de.extio.lmlib.client.cached;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

import de.extio.lmlib.client.ClientService;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.Conversation.TurnType;
import de.extio.lmlib.profile.ModelCategory;

@Disabled("This test requires a running Llama server or a cloud subscription (setup key in model profile)")
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({ TestCachedClientRepository.Config.class })
@PropertySource("classpath:/application-test.properties")
public class CachedClientTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CachedClientTest.class);
	
	@Autowired
	private TestCachedClientRepository cachedClientRepository;
	
	@Autowired
	private ClientService clientService;
	
	@AfterEach
	void afterEach() {
		this.cachedClientRepository.reset();
	}
	
	@Test
	void cacheCompletion() throws Exception {
		var completion = this.clientService.getClient(ModelCategory.SMALL).conversation(
				ModelCategory.SMALL,
				Conversation.create("You are a funny person", "Tell me a cat joke"),
				false);
		LOGGER.info(completion.toString());
		assertFalse(completion.statistics().cached());
		assertEquals(1, completion.statistics().requests());
		final var firstResponse = completion.response();
		assertFalse(firstResponse.isBlank());
		
		completion = this.clientService.getClient(ModelCategory.SMALL).conversation(
				ModelCategory.SMALL,
				Conversation.create("You are a funny person", "Tell me a cat joke"),
				false);
		LOGGER.info(completion.toString());
		assertTrue(completion.statistics().cached());
		assertEquals(0, completion.statistics().requests());
		assertEquals(firstResponse, completion.response());
		
		completion = this.clientService.getClient(ModelCategory.SMALL).conversation(
				ModelCategory.SMALL,
				Conversation.create("You are a funny person", "Tell me a dog joke"),
				false);
		LOGGER.info(completion.toString());
		assertFalse(completion.statistics().cached());
		assertEquals(1, completion.statistics().requests());
		assertNotEquals(firstResponse, completion.response());
	}
	
	@Test
	void cacheChat() throws Exception {
		final var conversation = Conversation.create("You are an assistant who stutters", "Hi there, I would like to know the climate in Munich.");
		
		var completion = this.clientService.getClient(ModelCategory.MEDIUM).conversation(ModelCategory.MEDIUM, conversation, false);
		LOGGER.info(completion.toString());
		assertFalse(completion.statistics().cached());
		assertEquals(1, completion.statistics().requests());
		final var firstResponse = completion.response();
		assertFalse(firstResponse.isBlank());
		
		completion = this.clientService.getClient(ModelCategory.MEDIUM).conversation(ModelCategory.MEDIUM, conversation, false);
		LOGGER.info(completion.toString());
		assertTrue(completion.statistics().cached());
		assertEquals(0, completion.statistics().requests());
		assertEquals(firstResponse, completion.response());
		
		conversation.addTurn(new Turn(TurnType.ASSISTANT,
				"T-t-the climate in B-b-berlin, um, it's, uh, quite, c-c-cool, actually. B-b-berlin has a, um, temperate climate, with, uh, four distinct s-s-seasons. S-s-spring is, um, pretty mild, with, uh, temperatures ranging from, uh, 10 to 20 degrees Celsius. S-s-summer is, um, warm, with, uh, highs around 25 degrees Celsius. F-f-fall is, um, cool, with, uh, temperatures ranging from 5 to 15 degrees Celsius. And, um, w-w-winter is, uh, quite, c-c-cold, with, uh, temperatures ranging from -2 to 5 degrees Celsius."));
		conversation.addTurn(new Turn(TurnType.USER, "Great! How is the weather in summer?"));
		completion = this.clientService.getClient(ModelCategory.MEDIUM).conversation(ModelCategory.MEDIUM, conversation, false);
		LOGGER.info(completion.toString());
		assertFalse(completion.statistics().cached());
		assertEquals(1, completion.statistics().requests());
		assertNotEquals(firstResponse, completion.response());
	}

	@Test
	void bypassCacheStillStoresFreshResult() throws Exception {
		final var conversation = Conversation.create("You are a witty assistant", "Give me a short joke about caches.");

		final var initialCompletion = this.clientService.getClient(ModelCategory.MEDIUM).conversation(ModelCategory.MEDIUM, conversation, false);
		LOGGER.info(initialCompletion.toString());
		assertFalse(initialCompletion.statistics().cached());
		assertEquals(1, initialCompletion.statistics().requests());
		assertFalse(initialCompletion.response().isBlank());

		final var bypassedCompletion = this.clientService.getClient(ModelCategory.MEDIUM).conversation(ModelCategory.MEDIUM, conversation, true);
		LOGGER.info(bypassedCompletion.toString());
		assertFalse(bypassedCompletion.statistics().cached());
		assertEquals(1, bypassedCompletion.statistics().requests());
		assertFalse(bypassedCompletion.response().isBlank());

		final var cachedCompletion = this.clientService.getClient(ModelCategory.MEDIUM).conversation(ModelCategory.MEDIUM, conversation, false);
		LOGGER.info(cachedCompletion.toString());
		assertTrue(cachedCompletion.statistics().cached());
		assertEquals(0, cachedCompletion.statistics().requests());
		assertEquals(bypassedCompletion.response(), cachedCompletion.response());
		assertEquals(bypassedCompletion.reasoning(), cachedCompletion.reasoning());
	}

	@Test
	void cacheStream() throws Exception {
		final var conversation = Conversation.create("You are an assistant who talks extremely like a pirate", "Hi there, I would like to know the climate in the red sea.");
		
		final var sb = new java.lang.StringBuilder();
		var completion = this.clientService.getClient(ModelCategory.MEDIUM).streamConversation(ModelCategory.MEDIUM, conversation, chunk -> {
			if (chunk.reasoningContent() != null) {
				System.out.print(chunk.reasoningContent());
			}
			if (chunk.content() != null) {	
				sb.append(chunk.content());
				System.out.print(chunk.content());
			}
		}, false);
		assertFalse(completion.statistics().cached());
		assertEquals(1, completion.statistics().requests());
		assertTrue(!sb.isEmpty());
		assertEquals(sb.toString(), completion.response());
		
		System.out.println();
		LOGGER.info("### Next request");

		final var sb2 = new java.lang.StringBuilder();
		completion = this.clientService.getClient(ModelCategory.MEDIUM).streamConversation(ModelCategory.MEDIUM, conversation, chunk -> {
			if (chunk.reasoningContent() != null) {
				System.out.print(chunk.reasoningContent());
			}
			if (chunk.content() != null) {
				sb2.append(chunk.content());
				System.out.print(chunk.content());
			}
		}, false);
		assertTrue(completion.statistics().cached());
		assertEquals(sb.toString(), sb2.toString());
		assertEquals(sb2.toString(), completion.response());
	}
}
