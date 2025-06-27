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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;

import de.extio.lmlib.client.ClientService;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.Conversation.Turn;
import de.extio.lmlib.client.Conversation.TurnType;
import de.extio.lmlib.profile.ModelCategory;

//@Disabled("This test requires a running Llama server or an Azure subscription (setup azure key in model profile)")
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@ComponentScan(basePackages = "de.extio.lmlib")
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
		var completion = this.clientService.getClient(ModelCategory.MEDIUM).completion(
				ModelCategory.MEDIUM,
				"You are a funny person",
				"Tell me a cat joke");
		LOGGER.info(completion.toString());
		assertFalse(completion.statistics().cached());
		assertEquals(1, completion.statistics().requests());
		final var firstResponse = completion.response();
		assertFalse(firstResponse.isBlank());
		
		completion = this.clientService.getClient(ModelCategory.MEDIUM).completion(
				ModelCategory.MEDIUM,
				"You are a funny person",
				"Tell me a cat joke");
		LOGGER.info(completion.toString());
		assertTrue(completion.statistics().cached());
		assertEquals(0, completion.statistics().requests());
		assertEquals(firstResponse, completion.response());
		
		completion = this.clientService.getClient(ModelCategory.MEDIUM).completion(
				ModelCategory.MEDIUM,
				"You are a funny person",
				"Tell me a dog joke");
		LOGGER.info(completion.toString());
		assertFalse(completion.statistics().cached());
		assertEquals(1, completion.statistics().requests());
		assertNotEquals(firstResponse, completion.response());
	}
	
	@Test
	void cacheChat() throws Exception {
		final var conversation = Conversation.create("You are an assistant who stutters", "Hi there, I would like to know the climate in Munich.");
		
		var completion = this.clientService.getClient(ModelCategory.MEDIUM).conversation(ModelCategory.MEDIUM, conversation);
		LOGGER.info(completion.toString());
		assertFalse(completion.statistics().cached());
		assertEquals(1, completion.statistics().requests());
		final var firstResponse = completion.response();
		assertFalse(firstResponse.isBlank());
		
		completion = this.clientService.getClient(ModelCategory.MEDIUM).conversation(ModelCategory.MEDIUM, conversation);
		LOGGER.info(completion.toString());
		assertTrue(completion.statistics().cached());
		assertEquals(0, completion.statistics().requests());
		assertEquals(firstResponse, completion.response());
		
		conversation.addTurn(new Turn(TurnType.ASSISTANT,
				"T-t-the climate in B-b-berlin, um, it's, uh, quite, c-c-cool, actually. B-b-berlin has a, um, temperate climate, with, uh, four distinct s-s-seasons. S-s-spring is, um, pretty mild, with, uh, temperatures ranging from, uh, 10 to 20 degrees Celsius. S-s-summer is, um, warm, with, uh, highs around 25 degrees Celsius. F-f-fall is, um, cool, with, uh, temperatures ranging from 5 to 15 degrees Celsius. And, um, w-w-winter is, uh, quite, c-c-cold, with, uh, temperatures ranging from -2 to 5 degrees Celsius."));
		conversation.addTurn(new Turn(TurnType.USER, "Great! How is the weather in summer?"));
		completion = this.clientService.getClient(ModelCategory.MEDIUM).conversation(ModelCategory.MEDIUM, conversation);
		LOGGER.info(completion.toString());
		assertFalse(completion.statistics().cached());
		assertEquals(1, completion.statistics().requests());
		assertNotEquals(firstResponse, completion.response());
	}
}
