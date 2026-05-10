package de.extio.lmlib.client.openrouter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import de.extio.lmlib.client.ClientService;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.grader.Grader2;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfileService;

@Disabled("This test requires a valid OpenRouter API key")
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@EnableAutoConfiguration
@TestPropertySource("classpath:/application-test.properties")
public class OpenRouterClientTest {

	private final static String API_KEY = ""; // Enter your OpenRouter API key here for testing

	private static final Logger LOGGER = LoggerFactory.getLogger(OpenRouterClientTest.class);

	@DynamicPropertySource
	static void registerProperties(final DynamicPropertyRegistry registry) {
		registry.add("OPENROUTER_API_KEY", () -> API_KEY);
	}

	@Autowired
	private OpenRouterClient openRouterClient;

	@Autowired
	private OpenRouterModelDiscovery openRouterModelDiscovery;

	@Autowired
	private ModelProfileService modelProfileService;

	@Autowired
	private ClientService clientService;

	@Test
	void modelDiscovery() throws Exception {
		final var modelProfile = this.modelProfile();
		final var models = this.openRouterModelDiscovery.listModels(modelProfile, new OpenRouterModelQuery(true, null, List.of(), List.of()), true);
		LOGGER.info("Models: {}", models);
		assertFalse(models.isEmpty());
		assertFalse(models.getFirst().getId().isBlank());
		final var resolvedModelName = models.getFirst().getId();
		LOGGER.info("Resolved model name: {}", resolvedModelName);
		final var metadata = this.openRouterModelDiscovery.resolveModelMetadata(modelProfile, resolvedModelName, true);
		LOGGER.info("Metadata: {}", metadata);
		assertEquals(resolvedModelName, metadata.modelId());
		assertTrue(metadata.contextLength() > 0);
		assertEquals(ModelProfile.ModelProvider.OPENROUTER, this.clientService.getClient(modelProfile).getModelProvider());
	}

	@Test
	void chatCompletion() throws Exception {
		final var modelProfile = this.modelProfile();
		final var completion = this.openRouterClient.conversation(
				modelProfile,
				Conversation.create("You are a helpful assistant", "Say the color is green"),
				null,
				false);

		LOGGER.info(completion.reasoning());
		LOGGER.info(completion.response());

		assertTrue(Grader2.assessScoreBinary("Does the text mention the color green?", completion.response(), modelProfile, this.clientService));
	}

	@Test
	void streamChatCompletion() throws Exception {
		final var modelProfile = this.modelProfile();
		final var conversation = Conversation.create("You are a helpful assistant", "I would like to play a game of chess.");
		conversation.addTurn(new Conversation.Turn(Conversation.TurnType.ASSISTANT, "Which opening do you choose?"));
		conversation.addTurn(new Conversation.Turn(Conversation.TurnType.USER, "You choose, but explain the opening to me."));
		final var responseBuilder = new StringBuilder();
		final var reasoningBuilder = new StringBuilder();
		final var completion = this.openRouterClient.streamConversation(modelProfile, conversation, chunk -> {
			if (chunk.reasoningContent() != null) {
				System.out.print(chunk.reasoningContent());
				reasoningBuilder.append(chunk.reasoningContent());
			}
			if (chunk.content() != null) {
				System.out.print(chunk.content());
				responseBuilder.append(chunk.content());
			}
		}, null, false);

		assertEquals(completion.response(), responseBuilder.toString());
		if (completion.reasoning() != null) {
			assertEquals(completion.reasoning(), reasoningBuilder.toString());
		}
		assertTrue(Grader2.assessScoreBinary("Does the text explain a chess opening?", completion.response(), modelProfile, this.clientService));
	}

	private ModelProfile modelProfile() {
		return this.modelProfileService.getModelProfile("profile.model.openrouter");
	}

}