package de.extio.lmlib.client.oai.completion.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

final class ChatChoice {
	
	public static final String FINISH_REASON_STOP = "stop";
	
	public static final String FINISH_REASON_LENGTH = "length";
	
	public static final String FINISH_REASON_CONTENT_FILTER = "content_filter";
	
	private ChatMessage message;
		
	private String finishReason;
	
	@JsonProperty("message")
	public ChatMessage getMessage() {
		return message;
	}

	public void setMessage(final ChatMessage message) {
		this.message = message;
	}

	@JsonProperty("finish_reason")
	public String getFinishReason() {
		return this.finishReason;
	}
	
	public void setFinishReason(final String finishReason) {
		this.finishReason = finishReason;
	}
	
	@Override
	public String toString() {
		return "ChatChoice [message=" + message + ", finishReason=" + finishReason + "]";
	}
	
}
