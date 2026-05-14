package de.extio.lmlib.client.oai.responses;

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
import org.springframework.context.annotation.Import;
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

@Disabled("This test requires a Responses API compatible endpoint")
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(ResponsesApiDialectTestConfiguration.class)
@TestPropertySource("classpath:/application-test.properties")
public class ResponsesApiClientTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(ResponsesApiClientTest.class);

	@Autowired
	private ResponsesApiClient responsesApiClient;

	@Autowired
	private ModelProfileService modelProfileService;

	@Autowired
	private ClientService clientService;

	@Autowired
	private ResponsesApiDialectTestConfiguration.MutableOpenAiResponsesApiDialect openAiResponsesApiDialect;

	@Test
	void listModels() throws Exception {
		final var modelProfile = this.modelProfile();
		final var modelNames = this.responsesApiClient.getModelNames(modelProfile, true);
		LOGGER.info("Models: {}", modelNames);
		assertFalse(modelNames.isEmpty());
		assertFalse(modelNames.getFirst().isBlank());
		assertEquals(ModelProfile.ModelProvider.OAI_RESPONSES, this.clientService.getClient(modelProfile).getModelProvider());
	}

	@Test
	void conversation() throws Exception {
		this.openAiResponsesApiDialect.setReasoning(OpenAiResponsesApiDialect.ReasoningEffort.NONE);
		runCompletion();
	}

	@Test
	void conversationWithReasoning() throws Exception {
		this.openAiResponsesApiDialect.setReasoning(OpenAiResponsesApiDialect.ReasoningEffort.MEDIUM);
		runCompletion();
	}
	
	private void runCompletion() {
		final var modelProfile = this.modelProfile();
		final var completion = this.responsesApiClient.conversation(
				modelProfile,
				Conversation.create("You are a helpful assistant", "Say the color is green"),
				null,
				false);

		LOGGER.info(completion.reasoning());
		LOGGER.info(completion.response());
		LOGGER.info(completion.statistics().toString());

		assertTrue(Grader2.assessScoreBinary("Does the text mention the color green?", completion.response(), modelProfile, this.clientService));
	}

	@Test
	void streamConversation() throws Exception {
		this.openAiResponsesApiDialect.setReasoning(OpenAiResponsesApiDialect.ReasoningEffort.NONE);
		runStreamedCompletion();
	}

	@Test
	void streamConversationWithReasoning() throws Exception {
		this.openAiResponsesApiDialect.setReasoning(OpenAiResponsesApiDialect.ReasoningEffort.MEDIUM);
		runStreamedCompletion();
	}

	private void runStreamedCompletion() {
		final var modelProfile = this.modelProfile();
		final var conversation = Conversation.create("You are a helpful assistant", "I would like to play a game of chess.");
		conversation.addTurn(new Conversation.Turn(Conversation.TurnType.ASSISTANT, "Which opening do you choose?"));
		conversation.addTurn(new Conversation.Turn(Conversation.TurnType.USER, "You choose, but explain the opening to me."));
		final var responseBuilder = new StringBuilder();
		final var reasoningBuilder = new StringBuilder();
		final var completion = this.responsesApiClient.streamConversation(modelProfile, conversation, chunk -> {
			if (chunk.reasoningContent() != null) {
				System.out.print(chunk.reasoningContent());
				reasoningBuilder.append(chunk.reasoningContent());
			}
			if (chunk.content() != null) {
				System.out.print(chunk.content());
				responseBuilder.append(chunk.content());
			}
		}, null, false);

		LOGGER.info(completion.statistics().toString());
		
		assertEquals(completion.response(), responseBuilder.toString());
		if (completion.reasoning() != null && !completion.reasoning().isBlank()) {
			assertEquals(completion.reasoning(), reasoningBuilder.toString());
		}
		assertTrue(Grader2.assessScoreBinary("Does the text explain a chess opening?", completion.response(), modelProfile, this.clientService));
	}

	@Test
	void toolCallingConversation() throws Exception {
		this.openAiResponsesApiDialect.setReasoning(OpenAiResponsesApiDialect.ReasoningEffort.MEDIUM);
		final var modelProfile = this.modelProfile();
		final var weatherTool = new ToolDefinition(
				"get_weather",
				"Gets the current weather for a given location.",
				ToolParameters.create(Map.of("location", "City and country, for example Berlin, Germany")),
				true);
		final var conversation = Conversation.create(
				"You are a helpful assistant. Use the provided tools when the user asks for location information. After receiving tool results, answer the question from the tool output.",
				"What is the weather in Berlin, Germany? Use the tool.");
		final var completion = this.responsesApiClient.conversation(
				modelProfile,
				conversation,
				ToolCallData.required(List.of(weatherTool)).withParallelToolCalls(true),
				false);

		LOGGER.info(completion.reasoning());
		LOGGER.info(completion.response());
		LOGGER.info("Tool calls: {}", completion.toolCalls());
		LOGGER.info(completion.statistics().toString());

		assertEquals(CompletionFinishReason.TOOL_CALLS, completion.finishReason());
		assertFalse(completion.outputItems().isEmpty());
		assertFalse(completion.toolCalls().isEmpty());
		final var weatherToolCall = this.findToolCall(completion.toolCalls(), "get_weather");
		assertTrue(weatherToolCall.arguments().contains("Berlin"));

		conversation.appendToolCallRound(completion, List.of(
				new Conversation.ToolResult(ToolParameters.create()
						.add("forecast", "Berlin is rainy and 21C")
						.json(), weatherToolCall.id())));

		final var finalCompletion = this.responsesApiClient.conversation(modelProfile, conversation, ToolCallData.auto(List.of(weatherTool)), false);
		LOGGER.info(finalCompletion.reasoning());
		LOGGER.info(finalCompletion.response());
		LOGGER.info(finalCompletion.statistics().toString());
		assertEquals(CompletionFinishReason.DONE, finalCompletion.finishReason());
		assertTrue(Grader2.assessScoreBinary("Does the text say that Berlin is rainy and 21C?", finalCompletion.response(), modelProfile, this.clientService));
	}

	@Test
	void streamToolCallingConversationBothRounds() throws Exception {
		this.openAiResponsesApiDialect.setReasoning(OpenAiResponsesApiDialect.ReasoningEffort.MEDIUM);
		final var modelProfile = this.modelProfile();
		final var weatherTool = new ToolDefinition(
				"get_weather",
				"Gets the current weather for a given location.",
				ToolParameters.create(Map.of("location", "City and country, for example Berlin, Germany")),
				true);
		final var conversation = Conversation.create(
				"You are a helpful assistant. Use the provided tools when the user asks for location information. After receiving tool results, answer the question from the tool output.",
				"What is the weather in Berlin, Germany? Use the tool.");
		final var firstRoundResponseBuilder = new StringBuilder();
		final var firstRoundReasoningBuilder = new StringBuilder();
		final var completion = this.responsesApiClient.streamConversation(
				modelProfile,
				conversation,
				chunk -> {
					if (chunk.reasoningContent() != null) {
						System.out.print(chunk.reasoningContent());
						firstRoundReasoningBuilder.append(chunk.reasoningContent());
					}
					if (chunk.content() != null) {
						System.out.print(chunk.content());
						firstRoundResponseBuilder.append(chunk.content());
					}
				},
				ToolCallData.required(List.of(weatherTool)).withParallelToolCalls(true),
				false);

		LOGGER.info(completion.reasoning());
		LOGGER.info(completion.response());
		LOGGER.info("Tool calls: {}", completion.toolCalls());
		LOGGER.info(completion.statistics().toString());

		assertEquals(CompletionFinishReason.TOOL_CALLS, completion.finishReason());
		assertEquals(completion.response(), firstRoundResponseBuilder.toString());
		if (completion.reasoning() != null && !completion.reasoning().isBlank()) {
			assertEquals(completion.reasoning(), firstRoundReasoningBuilder.toString());
		}
		assertFalse(completion.outputItems().isEmpty());
		assertFalse(completion.toolCalls().isEmpty());
		final var weatherToolCall = this.findToolCall(completion.toolCalls(), "get_weather");
		assertTrue(weatherToolCall.arguments().contains("Berlin"));

		conversation.appendToolCallRound(completion, List.of(
				new Conversation.ToolResult(ToolParameters.create()
						.add("forecast", "Berlin is rainy and 21C")
						.json(), weatherToolCall.id())));

		final var secondRoundResponseBuilder = new StringBuilder();
		final var secondRoundReasoningBuilder = new StringBuilder();
		final var finalCompletion = this.responsesApiClient.streamConversation(
				modelProfile,
				conversation,
				chunk -> {
					if (chunk.reasoningContent() != null) {
						System.out.print(chunk.reasoningContent());
						secondRoundReasoningBuilder.append(chunk.reasoningContent());
					}
					if (chunk.content() != null) {
						System.out.print(chunk.content());
						secondRoundResponseBuilder.append(chunk.content());
					}
				},
				ToolCallData.auto(List.of(weatherTool)),
				false);

		LOGGER.info(finalCompletion.reasoning());
		LOGGER.info(finalCompletion.response());
		LOGGER.info(finalCompletion.statistics().toString());

		assertEquals(CompletionFinishReason.DONE, finalCompletion.finishReason());
		assertEquals(finalCompletion.response(), secondRoundResponseBuilder.toString());
		if (finalCompletion.reasoning() != null && !finalCompletion.reasoning().isBlank()) {
			assertEquals(finalCompletion.reasoning(), secondRoundReasoningBuilder.toString());
		}
		assertTrue(Grader2.assessScoreBinary("Does the text say that Berlin is rainy and 21C?", finalCompletion.response(), modelProfile, this.clientService));
	}

	private ModelProfile modelProfile() {
		return this.modelProfileService.getModelProfile("profile.model.responses");
	}

	private ToolCall findToolCall(final List<ToolCall> toolCalls, final String toolName) {
		return toolCalls.stream()
				.filter(toolCall -> toolName.equals(toolCall.name()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing tool call: " + toolName));
	}

}
