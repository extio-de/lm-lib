package de.extio.lmlib.client.oai.completion.chat;

import java.util.List;

import com.fasterxml.jackson.annotation.*;

import de.extio.lmlib.client.oai.completion.AbstractCompletionResponse;

final class ChatCompletionResponse extends AbstractCompletionResponse {
	
	private List<ChatChoice> choices;
	
	@JsonProperty("choices")
	public List<ChatChoice> getChoices() {
		return this.choices;
	}
	
	public void setChoices(final List<ChatChoice> choices) {
		this.choices = choices;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("CompletionResponse [id=");
		builder.append(this.id);
		builder.append(", object=");
		builder.append(this.object);
		builder.append(", created=");
		builder.append(this.created);
		builder.append(", choices=");
		builder.append(this.choices);
		builder.append(", usage=");
		builder.append(this.usage);
		builder.append("]");
		return builder.toString();
	}
}
