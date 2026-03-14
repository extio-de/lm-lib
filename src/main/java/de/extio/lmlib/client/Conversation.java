package de.extio.lmlib.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
		builder.append("]");
		return builder.toString();
	}
	
	public enum TurnType {
		SYSTEM, USER, ASSISTANT
	}
	
	public record Turn(TurnType type, String text) {
	}
	
}
