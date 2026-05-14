package de.extio.lmlib.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Mutable conversation history shared across lm-lib clients and agents.
 */
public final class Conversation {
	
	public static Conversation create(final String system, final String user) {
		final Conversation conversation = new Conversation();
		if (system != null && !system.isEmpty()) {
			conversation.addTurn(new Turn(TurnType.SYSTEM, system));
		}
		if (user != null && !user.isEmpty()) {
			conversation.addTurn(new Turn(TurnType.USER, user));
		}
		return conversation;
	}
	
	public static Conversation create(final String user) {
		final Conversation conversation = new Conversation();
		if (user != null && !user.isEmpty()) {
			conversation.addTurn(new Turn(TurnType.USER, user));
		}
		return conversation;
	}
	
	public static Conversation copy(final Conversation conversation) {
		final Conversation copy = new Conversation();
		if (conversation != null) {
			synchronized (conversation.conversation) {
				copy.conversation.addAll(conversation.conversation);
			}
		}
		return copy;
	}
	
	private final List<Turn> conversation = Collections.synchronizedList(new ArrayList<>());

	private String metadata; // Optional metadata that can be used to store additional information about the conversation, e.g. conversation id, user id, etc.
	
	private Conversation() {
	}
	
	public void addTurn(final Turn turn) {
		this.conversation.add(turn);
	}
	
	public void replaceTurn(final Turn turn) {
		synchronized (this.conversation) {
			if (!this.conversation.isEmpty()) {
				this.conversation.set(this.conversation.size() - 1, turn);
			}
			else {
				this.addTurn(turn);
			}
		}
	}

	/**
	 * Appends the assistant tool-call round and the corresponding tool results.
	 * <p>
	 * When the completion contains structured output items, they are preserved on
	 * the assistant turn so Responses-style stateless replay can round-trip the
	 * provider payload untouched.
	 *
	 * @param completion the completion that requested tool execution
	 * @param toolResults the tool outputs to append after the assistant turn
	 */
	public void appendToolCallRound(final Completion completion, final List<ToolResult> toolResults) {
		if (completion == null) {
			throw new IllegalArgumentException("completion must not be null");
		}
		final var assistantText = completion.toolCalls().isEmpty() ? completion.response() : "";
		this.appendToolCallRound(assistantText, completion.toolCalls(), toolResults, null, completion.outputItems());
	}

	/**
	 * Appends an assistant tool-call round using only normalized lm-lib fields.
	 *
	 * @param assistantText assistant text to append before the tool results
	 * @param toolCalls tool calls returned by the provider
	 * @param toolResults tool outputs to append after the assistant turn
	 */
	public void appendToolCallRound(final String assistantText, final List<ToolCall> toolCalls, final List<ToolResult> toolResults) {
		this.appendToolCallRound(assistantText, toolCalls, toolResults, null, List.of());
	}

	/**
	 * Appends an assistant tool-call round including provider-specific replay
	 * metadata.
	 *
	 * @param assistantText assistant text to append before the tool results
	 * @param toolCalls tool calls returned by the provider
	 * @param toolResults tool outputs to append after the assistant turn
	 * @param phase optional assistant phase metadata for providers that emit it
	 * @param outputItems provider-specific structured output items that should be
	 *        preserved for follow-up requests
	 */
	public void appendToolCallRound(final String assistantText, final List<ToolCall> toolCalls, final List<ToolResult> toolResults, final String phase, final List<Map<String, Object>> outputItems) {
		this.addTurn(new Turn(TurnType.ASSISTANT, assistantText == null ? "" : assistantText, toolCalls, null, phase, outputItems));
		if (toolResults == null || toolResults.isEmpty()) {
			return;
		}
		for (final var toolResult : toolResults) {
			this.addTurn(new Turn(TurnType.TOOL, toolResult.text(), null, toolResult.toolCallId(), null, List.of()));
		}
	}

	public void rewindTurn() {
		synchronized (this.conversation) {
			if (this.conversation.size() > 1) {
				this.conversation.removeLast();
				this.conversation.removeLast();
			}
		}
	}
	
	public List<Turn> getConversation() {
		synchronized (this.conversation) {
			return Collections.unmodifiableList(this.conversation);
		}
	}
	
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("Conversation [conversation=");
		builder.append(this.conversation);
		if (this.metadata != null) {
			builder.append(", metadata=");
			builder.append(this.metadata);
		}
		builder.append("]");
		return builder.toString();
	}

	/**
	 * Optional metadata associated with the whole conversation, for example a user
	 * id or an upstream conversation id.
	 */
	public String getMetadata() {
		return metadata;
	}

	/**
	 * Sets optional conversation-level metadata.
	 */
	public void setMetadata(final String metadata) {
		this.metadata = metadata;
	}
	
	/**
	 * Turn roles supported by lm-lib conversations.
	 */
	public enum TurnType {
		SYSTEM, USER, ASSISTANT, TOOL
	}
	
	/**
	 * Single conversation turn.
	 *
	 * @param type logical role of the turn
	 * @param text normalized text payload for the turn
	 * @param toolCalls tool calls attached to assistant turns
	 * @param toolCallId tool-call id referenced by tool result turns
	 * @param phase optional assistant phase metadata used by Responses-style replay
	 * @param outputItems provider-specific structured output items that should be
	 *        replayed verbatim in later requests
	 */
	public record Turn(TurnType type, String text, List<ToolCall> toolCalls, String toolCallId, String phase, List<Map<String, Object>> outputItems) {

		public Turn {
			toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
			outputItems = outputItems == null ? List.of() : outputItems.stream().map(outputItem -> outputItem == null ? Map.<String, Object>of() : Map.copyOf(outputItem)).toList();
		}

		/**
		 * Creates a turn without provider-specific replay metadata.
		 */
		public Turn(final TurnType type, final String text, final List<ToolCall> toolCalls, final String toolCallId) {
			this(type, text, toolCalls, toolCallId, null, List.of());
		}

		/**
		 * Creates a plain turn with an explicit assistant phase and no tool-call or
		 * replay payload.
		 */
		public Turn(final TurnType type, final String text, final String phase) {
			this(type, text, List.of(), null, phase, List.of());
		}

		/**
		 * Creates a plain turn without tool-call or replay metadata.
		 */
		public Turn(final TurnType type, final String text) {
			this(type, text, List.of(), null, null, List.of());
		}
	}

	/**
	 * Result payload returned by a caller after executing a tool.
	 *
	 * @param text serialized tool output
	 * @param toolCallId tool-call id the output belongs to
	 */
	public record ToolResult(String text, String toolCallId) {

		public ToolResult {
			text = text == null ? "" : text;
		}
	}
	
}
