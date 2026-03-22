package de.extio.lmlib.grader.agent;

import java.util.List;

import de.extio.lmlib.agent.Agent;
import de.extio.lmlib.agent.AgentNext;
import de.extio.lmlib.agent.AgentType;
import de.extio.lmlib.agent.responsehandler.JsonAgentResponseHandler;
import de.extio.lmlib.profile.ModelCategory;

/**
 * @deprecated Use {@link AnswerGrader2} instead.
 */
@Deprecated
public class AnswerGrader {
	
	public static Agent createAgent() {
		return new Agent(
				"AnswerGrader",
				AgentType.COMPLETION,
				ModelCategory.MEDIUM,
				null,
				"""
						You are a grader agent that evaluates the quality of an answer to a support question.
						Please provide a response on a scale between 0 and 100, where: 100 indicates that the answer directly addresses the question, is accurate, and provides a clear and concise solution or explanation; 90-99 indicates that the answer is mostly accurate and provides a good solution or explanation, but may lack some detail or clarity; 80-89 indicates that the answer is generally accurate but may require some additional information or clarification to fully understand the solution or explanation; 75 indicates that the answer describes how the system behaves to get to the subject of the question, but may not directly answer the question; 50-74 indicates that the answer is partially accurate or relevant, but may lack key information or provide an incomplete solution or explanation; 25-49 indicates that the answer does not directly answer the question or is speculative or it contains mainly instructions for changes or mainly refers to the Enterprise Integration team without providing substantial information, but may provide some relevant information or context; 0-24 indicates that the answer is not related to the question, is inaccurate, or provides no useful information;
						Please evaluate the answer and provide a rating between 0 and 100 based on its quality and relevance to the question.
						Provide no preamble and a short explanation. Return the response in JSON format with the following fields: {\"qaRating\": rating, \"qaExplanation\": \"explanation\"}
						""",
				"""
						The question is: {{questions}}
						The answer is: {{answer}}
						""",
				null,
				new JsonAgentResponseHandler(),
				context -> {
					context.setSkipCache(true);
				},
				context -> {
					if (!context.getContext().containsKey("qaRating")) {
						context.setStringValue("qaRating", "0");
						context.setStringValue("qaExplanation", "The grader agent did not provide an answer");
					}
					else if (!context.getContext().containsKey("qaExplanation")) {
						context.setStringValue("qaExplanation", "The grader agent did not provide an explanation");
					}
					
					context.getContext().compute("qaIteration", (k, v) -> {
						if (v == null) {
							return List.of(1);
						}
						else {
							return List.of((Integer) v.get(0) + 1);
						}
					});
				},
				null,
				context -> {
					final var qaRatingEntry = context.getStringValue("qaRating");
					final var qaRating = qaRatingEntry != null ? Integer.parseInt(qaRatingEntry) : 0;
					final var qaPassed = qaRating > 60;
					context.getContext().put("qaPassed", List.of(qaPassed));
					if (!qaPassed && (int) context.getContext().get("qaIteration").get(0) < 2) {
						context.setStringValue("qaFeedback", "Feedback from the previous attempt to consider: " + context.getContext().get("qaExplanation").get(0).toString());
						return new AgentNext(context.getStringValue("qaFeedbackLoopDestination"), Boolean.FALSE);
					}
					
					return new AgentNext(null, qaPassed);
				});
	}
}
