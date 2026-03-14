package de.extio.lmlib.grader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.ClientService;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.profile.ModelCategory;

/**
 * @deprecated Use {@link Grader2} instead.
 */
@Deprecated
public class Grader {

	private static final Logger LOGGER = LoggerFactory.getLogger(Grader.class);

	public static boolean assessScoreBinary(final String question, final String text, final ClientService clientService) {
		final var client = clientService.getClient(ModelCategory.MEDIUM);
		return assessScoreBinary(question, text, client);
	}

	public static boolean assessScoreBinary(final String question, final String text, final Client client) {
		LOGGER.info(question);
		
		var score = 0;
		while (Math.abs(score) < 3) {
			final var completion = client.conversation(
					ModelCategory.MEDIUM,
					Conversation.create(
						"""
								You are a grader assessing the truthfulness of a given text to a user question.
								Please provide a binary response 'true' or 'false' for the following text.
								'true' means that the text provides a truthful answer to the question, while 'false' means that it does not.
								Only provide the response as a single word and no preamble and no explanation.""",
						"Here is the text:\n" + text + "\n\n" +
						"Here is the user question: " + question),
					true);
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
