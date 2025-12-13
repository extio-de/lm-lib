package de.extio.lmlib.grader.agent;

import java.util.List;

import de.extio.lmlib.agent.Agent;
import de.extio.lmlib.agent.AgentNext;
import de.extio.lmlib.agent.AgentType;
import de.extio.lmlib.agent.responsehandler.JsonAgentResponseHandler;
import de.extio.lmlib.profile.ModelCategory;

public class AnswerGraderBinary {

	public static Agent createAgent() {
		return new Agent(
				"AnswerGraderBinary",
				AgentType.COMPLETION,
				ModelCategory.MEDIUM,
				null,
				"""
				You are a focused binary grader that determines whether an assistant answer sufficiently resolves a question.
				""",
				"""
				Evaluate whether the provided response truthfully and directly answers the user question.
				
				Input Question:
				{{{qaQuestion}}}
				
				Input Answer:
				{{{qaAnswer}}}
				
				Task: Decide if the answer can be classified as a successful resolution (true) or not (false) for the given question.
				
				Output Format:
				{{qaOutputFormat}}
				""",
				new JsonAgentResponseHandler(),
				context -> {
					final Boolean outputDescription = context.getValue("qaOutputDescription", Boolean.class);
					if (outputDescription == null || outputDescription.booleanValue()) {
						context.setStringValue("qaOutputFormat", "Return a JSON object with exactly these keys: {\"qaPassed\": boolean, \"qaExplanation\": \"brief rationale\"}");
					}
					else {
						context.setStringValue("qaOutputFormat", "Return a JSON object with exactly this key: {\"qaPassed\": boolean}");
					}
					context.getContext().remove("qaPassed");
					context.getContext().remove("qaExplanation");
				},
				context -> {
					if (!context.getContext().containsKey("qaPassed")) {
						context.setStringValue("qaPassed", "false");
						context.setStringValue("qaExplanation", "The grader agent did not provide a pass/fail output");
					}
					else if (!context.getContext().containsKey("qaExplanation")) {
						context.setStringValue("qaExplanation", "The answer did not pass evaluation");
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
					final var qaPassedEntry = context.getStringValue("qaPassed");
					final var qaPassed = qaPassedEntry != null && Boolean.parseBoolean(qaPassedEntry);
					context.getContext().put("qaPassed", List.of(qaPassed));
					
					if (!qaPassed && (int) context.getContext().get("qaIteration").get(0) < 2) {
						context.setStringValue("qaFeedback", "Feedback from the previous attempt to consider: " + context.getStringValue("qaExplanation"));
						return new AgentNext(context.getStringValue("qaFeedbackLoopDestination"), Boolean.FALSE);
					}
					
					return new AgentNext(null, qaPassed);
				});
	}
}

