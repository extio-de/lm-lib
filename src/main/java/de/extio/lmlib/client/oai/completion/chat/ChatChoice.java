package de.extio.lmlib.client.oai.completion.chat;

import com.fasterxml.jackson.annotation.*;

class ChatChoice {
	
	protected ChatMessage message;
		
	protected String finishReason;
	
	@JsonProperty("message")
	public ChatMessage getMessage() {
		return message;
	}

	public void setMessage(final ChatMessage message) {
		this.message = message;
	}

	@JsonProperty("delta")
	public ChatMessage getDelta() {
		return message;
	}

	public void setDelta(final ChatMessage delta) {
		this.message = delta;
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
