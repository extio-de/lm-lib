package de.extio.lmlib.client;

public enum CompletionFinishReason {

	/**
	 * Regular completion
	 */
	DONE,

	/**
	 * Completion was stopped because the token limit was reached. You may continue the conversation.
	 */
	TOKEN_LIMIT_REACHED,

	/**
	 * Completion was stopped because the content was filtered.
	 */
	CONTENT_FILTERED

}