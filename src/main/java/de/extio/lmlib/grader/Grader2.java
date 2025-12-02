package de.extio.lmlib.grader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.extio.lmlib.client.ClientService;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfileService;

public class Grader2 {

	private static final Logger LOGGER = LoggerFactory.getLogger(Grader2.class);

	public static boolean assessScoreBinary(final String question, final String text, final ModelCategory modelCategory, final ModelProfileService modelProfileService, final ClientService clientService) {
		final var modelProfile = modelProfileService.getModelProfile(modelCategory);
		return assessScoreBinary(question, text, modelProfile, clientService);
	}

	public static boolean assessScoreBinary(final String question, final String text, final ModelProfile modelProfile, final ClientService clientService) {
		LOGGER.info(question);

		final var client = clientService.getClient(modelProfile);
		
		var score = 0;
		while (Math.abs(score) < 3) {
			final var completion = client.conversation(
					modelProfile,
					Conversation.create(
					"""
					You are an expert grader. Your role is to assess the truthfulness of a given text in response to a user question.
					""",
					"""
					I need you to determine if the provided text is a truthful answer to the question.
					
					### Input Data
					**Question:**
					%s
					
					**Text:**
					%s
					
					### Task
					Assess whether the text provides a truthful answer to the question.
					
					### Output Format
					Provide a binary response 'true' or 'false'.
					- 'true': The text provides a truthful answer.
					- 'false': The text does not provide a truthful answer.
					Only provide the response as a single word. Do not include any preamble or explanation.
					""".formatted(question, text)));
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
