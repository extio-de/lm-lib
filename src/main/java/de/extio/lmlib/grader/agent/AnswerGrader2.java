package de.extio.lmlib.grader.agent;

import java.util.List;

import de.extio.lmlib.agent.Agent;
import de.extio.lmlib.agent.AgentNext;
import de.extio.lmlib.agent.AgentType;
import de.extio.lmlib.agent.responsehandler.JsonAgentResponseHandler;
import de.extio.lmlib.profile.ModelCategory;

public class AnswerGrader2 {
	
	public static Agent createAgent() {
		return new Agent(
				"AnswerGrader",
				AgentType.COMPLETION,
				ModelCategory.MEDIUM,
				null,
						"""
						You are an expert grader agent. Your role is to evaluate the quality of an answer provided for a specific question.
						""",
						"""
						I need you to evaluate the quality of the answer provided below.
						
						### Input Data
						**Question:**
						{{{qaQuestion}}}
						
						**Answer:**
						{{{qaAnswer}}}
						
						### Task
						Evaluate the answer and provide a rating between 0 and 100 based on its quality and relevance to the question. Use the following scale:
						- 100: Directly addresses the question, is accurate, and provides a clear and concise solution or explanation.
						- 90-99: Mostly accurate and provides a good solution or explanation, but may lack some detail or clarity.
						- 80-89: Generally accurate but may require some additional information or clarification to fully understand the solution or explanation.
						- 60-74: Partially accurate or relevant, provides a useful partial solution or explanation, but misses some key information.
						- 50-59: Relevant to the general topic but provides an incomplete solution or explanation that does not sufficiently answer the specific question.
						- 25-49: Does not directly answer the question, is speculative, contains mainly instructions for changes, or refers to external teams without providing substantial information, but may provide some relevant information or context.
						- 0-24: Not related to the question, is inaccurate, or provides no useful information.
						
						### Output Format
						{{qaOutputFormat}}
						""",
				new JsonAgentResponseHandler(),
				context -> {
					final Boolean outputDescription = context.getValue("qaOutputDescription", Boolean.class);
					if (outputDescription == null || outputDescription.booleanValue()) {
						context.setStringValue("qaOutputFormat", "Provide no preamble and a short explanation. Return the response in JSON format with the following fields: {\"qaRating\": rating, \"qaExplanation\": \"brief explanation\"}");
					}
					else {
						context.setStringValue("qaOutputFormat", "Provide no preamble. Return the response in JSON format with the following field only: {\"qaRating\": rating}");
					}
					context.getContext().remove("qaRating");
					context.getContext().remove("qaExplanation");
					context.setSkipCache(true);
				},
				context -> {
					if (!context.getContext().containsKey("qaRating")) {
						context.setStringValue("qaRating", "0");
						context.setStringValue("qaExplanation", "The grader agent did not provide an answer");
					}
					else if (!context.getContext().containsKey("qaExplanation")) {
						final var qaRatingEntry = context.getStringValue("qaRating");
						context.setStringValue("qaExplanation", "Rating was not sufficient: " + qaRatingEntry + " out of 100.");
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
						context.setStringValue("qaFeedback", "Feedback from the previous attempt to consider: " + context.getStringValue("qaExplanation"));
						return new AgentNext(context.getStringValue("qaFeedbackLoopDestination"), Boolean.FALSE);
					}
					
					return new AgentNext(null, qaPassed);
				});
	}
}
