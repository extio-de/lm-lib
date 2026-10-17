package de.extio.lmlib.client.anthropic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;

import de.extio.lmlib.client.ClientService;
import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.ToolCall;
import de.extio.lmlib.client.ToolCallData;
import de.extio.lmlib.client.ToolDefinition;
import de.extio.lmlib.client.ToolParameters;
import de.extio.lmlib.grader.Grader2;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfileService;

@Disabled("This test requires a running Anthropic-compatible server at the configured URL")
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@EnableAutoConfiguration
@TestPropertySource("classpath:/application-test.properties")
public class AnthropicClientTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(AnthropicClientTest.class);

	@Autowired
	private AnthropicClient anthropicClient;

	@Autowired
	private ModelProfileService modelProfileService;

	@Autowired
	private ClientService clientService;

	@Test
	void routesProfileToAnthropicClient() throws Exception {
		final var modelProfile = this.modelProfile();
		assertEquals(ModelProfile.ModelProvider.ANTHROPIC, this.clientService.getClient(modelProfile).getModelProvider());
		assertTrue(this.anthropicClient.supportsToolCalling(modelProfile));
	}

	@Test
	void listModels() throws Exception {
		final var modelProfile = this.modelProfile();
		final var modelNames = this.anthropicClient.getModelNames(modelProfile, true);
		AnthropicClientTest.LOGGER.info("Models: {}", modelNames);
		assertFalse(modelNames.isEmpty());
		assertFalse(modelNames.getFirst().isBlank());
	}

	@Test
	void chatCompletion() throws Exception {
		final var modelProfile = this.modelProfile();
		final var completion = this.anthropicClient.conversation(
				modelProfile,
				Conversation.create("You are a helpful assistant", "Say the color is green"),
				null,
				false);

		LOGGER.info("Reasoning: {}", completion.reasoning());
		LOGGER.info("Response: {}", completion.response());

		assertFalse(completion.response().isBlank());
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
		final var completion = this.anthropicClient.streamConversation(modelProfile, conversation, chunk -> {
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
		if (!completion.reasoning().isBlank()) {
			assertEquals(completion.reasoning(), reasoningBuilder.toString());
		}
		assertTrue(Grader2.assessScoreBinary("Does the text explain a chess opening?", completion.response(), modelProfile, this.clientService));
	}

	@Test
	void toolCallingChatCompletion() throws Exception {
		final var modelProfile = this.modelProfile();
		final var weatherTool = new ToolDefinition(
				"get_weather",
				"Gets the current weather for a given location.",
				ToolParameters.create(Map.of("location", "City and country, for example Berlin, Germany")),
				null);
		final var conversation = Conversation.create(
				"You are a helpful assistant. Use the provided tools when the user asks for location information. After receiving tool results, answer the question from the tool output.",
				"What is the weather in Berlin, Germany? Use the tool.");
		final var completion = this.anthropicClient.conversation(
				modelProfile,
				conversation,
				ToolCallData.required(List.of(weatherTool)),
				false);

		LOGGER.info("Reasoning: {}", completion.reasoning());
		LOGGER.info("Response: {}", completion.response());
		LOGGER.info("Tool calls: {}", completion.toolCalls());

		assertEquals(CompletionFinishReason.TOOL_CALLS, completion.finishReason());
		assertFalse(completion.toolCalls().isEmpty());
		final var weatherToolCall = this.findToolCall(completion.toolCalls(), "get_weather");
		assertTrue(weatherToolCall.arguments().contains("Berlin"));

		conversation.appendToolCallRound(completion, List.of(
				new Conversation.ToolResult(ToolParameters.create()
						.add("forecast", "Berlin is rainy and 21C")
						.json(), weatherToolCall.id())));
		LOGGER.info("Conversation after tool call: {}", conversation.getConversation());

		final var finalCompletion = this.anthropicClient.conversation(modelProfile, conversation, ToolCallData.auto(List.of(weatherTool)), false);
		LOGGER.info("Reasoning: {}", finalCompletion.reasoning());
		LOGGER.info("Response: {}", finalCompletion.response());
		LOGGER.info("Tool calls: {}", finalCompletion.toolCalls());
		assertEquals(CompletionFinishReason.DONE, finalCompletion.finishReason());
		assertTrue(Grader2.assessScoreBinary("Does the text say that Berlin is rainy and 21C?", finalCompletion.response(), modelProfile, this.clientService));
	}

	private ModelProfile modelProfile() {
		return this.modelProfileService.getModelProfile("profile.model.anthropic");
	}

	private ToolCall findToolCall(final List<ToolCall> toolCalls, final String toolName) {
		return toolCalls.stream()
				.filter(toolCall -> toolName.equals(toolCall.name()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing tool call: " + toolName));
	}

}
