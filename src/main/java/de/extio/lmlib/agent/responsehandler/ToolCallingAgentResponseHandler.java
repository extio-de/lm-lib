package de.extio.lmlib.agent.responsehandler;

import java.util.ArrayList;
import java.util.List;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.ToolCall;
import de.extio.lmlib.client.ToolParameters;

public class ToolCallingAgentResponseHandler implements AgentResponseHandler {

	@FunctionalInterface
	public interface ToolCallHandler {

		boolean handle(Completion completion, AgentContext context, ToolCallResults toolCallResults);

	}

	public record ToolCallResult(String toolCallId, String output) {

	}

	public static final class ToolCallResults {

		private final List<ToolCallResult> toolCallResults = new ArrayList<>();

		public ToolCallResults add(final ToolCall toolCall, final String output) {
			return this.add(toolCall.id(), output);
		}

		public ToolCallResults add(final ToolCall toolCall, final ToolParameters output) {
			return this.add(toolCall.id(), output.json());
		}

		public ToolCallResults add(final String toolCallId, final String output) {
			this.toolCallResults.add(new ToolCallResult(toolCallId, output));
			return this;
		}

		public List<ToolCallResult> results() {
			return List.copyOf(this.toolCallResults);
		}

	}

	private final AgentResponseHandler delegate;

	private final ToolCallHandler toolCallHandler;

	public ToolCallingAgentResponseHandler(final AgentResponseHandler delegate, final ToolCallHandler toolCallHandler) {
		this.delegate = delegate;
		this.toolCallHandler = toolCallHandler;
	}

	@Override
	public boolean handle(final Completion completion, final AgentContext context) {
		return this.delegate == null || this.delegate.handle(completion, context);
	}

	public boolean handleToolCalls(final Completion completion, final AgentContext context, final ToolCallResults toolCallResults) {
		return this.toolCallHandler.handle(completion, context, toolCallResults);
	}

}