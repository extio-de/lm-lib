package de.extio.lmlib.client.azureai;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
import de.extio.lmlib.grader.Grader2;
import de.extio.lmlib.profile.ModelProfileService;

@Disabled("This test requires an Azure subscription (setup azure key in model profile)")
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@ComponentScan(basePackages = "de.extio.lmlib")
@PropertySource("classpath:/application-test.properties")
public class AzureAiClientTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AzureAiClientTest.class);
	
	@Autowired
	private AzureAiClient client;
	
	@Autowired
	private ModelProfileService modelProfileService;
	
	@Autowired
	private ClientService clientService;
	
	@Test
	void completion() throws Exception {
		final var modelProfile = this.modelProfileService.getModelProfile("profile.model.azure");
		final var completion = this.client.conversation(
				modelProfile,
				Conversation.create("You are a clever story teller", "Continue this story:\nOnce upon a time, there was a spaceship travelling to a black hole. The crew were cats!"));
		LOGGER.info(completion.response());
		
		assertTrue(Grader2.assessScoreBinary("Does the story continue about a spaceship with cat crew?", completion.response(), modelProfile, this.clientService));
	}
	
	@Test
	void completionLarge() throws Exception {
		final var modelProfile = this.modelProfileService.getModelProfile("profile.model.azure");
		final var completion = this.client.conversation(
				modelProfile,
				Conversation.create("You are a clever story teller", "Continue this story:\nOnce upon a time, there was a spaceship travelling to a black hole. The crew were cats!"));
		LOGGER.info(completion.response());
		
		assertTrue(Grader2.assessScoreBinary("Does the story continue about a spaceship with cat crew?", completion.response(), modelProfile, this.clientService));
	}
	
	@Test
	void chat() throws Exception {
		final var modelProfile = this.modelProfileService.getModelProfile("profile.model.azure");
		final var conversation = Conversation.create("You are an assistant who stutters", "Hi there, I would like to know the climate in Berlin.");
		conversation.addTurn(new Turn(TurnType.ASSISTANT,
				"T-t-the climate in B-b-berlin, um, it's, uh, quite, c-c-cool, actually. B-b-berlin has a, um, temperate climate, with, uh, four distinct s-s-seasons. S-s-spring is, um, pretty mild, with, uh, temperatures ranging from, uh, 10 to 20 degrees Celsius. S-s-summer is, um, warm, with, uh, highs around 25 degrees Celsius. F-f-fall is, um, cool, with, uh, temperatures ranging from 5 to 15 degrees Celsius. And, um, w-w-winter is, uh, quite, c-c-cold, with, uh, temperatures ranging from -2 to 5 degrees Celsius."));
		conversation.addTurn(new Turn(TurnType.USER, "Great! How is the weather in summer?"));
		
		final var completion = this.client.conversation(modelProfile, conversation);
		LOGGER.info(completion.response());
		
		assertTrue(Grader2.assessScoreBinary("Does the response mention summer weather?", completion.response(), modelProfile, this.clientService));
	}
	
	@Test
	void longPrompt() throws Exception {
		final var modelProfile = this.modelProfileService.getModelProfile("profile.model.azure");
		final var completion = this.client.conversation(
				modelProfile,
				Conversation.create("You are a helpful assistant", "Count the numbers: 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9 1 2 3 4 5 6 7 8 9"));
		LOGGER.info(completion.response());
		
		assertTrue(Grader2.assessScoreBinary("Did the assistant count the numbers?", completion.response(), modelProfile, this.clientService));
	}
}
