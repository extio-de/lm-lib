package de.extio.lmlib.client.oai.completion.text;

import java.util.List;

import com.fasterxml.jackson.annotation.*;

import de.extio.lmlib.client.oai.completion.AbstractCompletionResponse;

final class CompletionResponse extends AbstractCompletionResponse {
	
	private List<Choice> choices;
	
	private String content;
	
	private Boolean isStoppedEos;
	
	@JsonProperty("choices")
	public List<Choice> getChoices() {
		return this.choices;
	}
	
	public void setChoices(final List<Choice> choices) {
		this.choices = choices;
	}
	
	@JsonProperty("content")
	public String getContent() {
		return this.content;
	}
	
	public void setContent(final String content) {
		this.content = content;
	}
	
	@JsonProperty("stopped_eos")
	public Boolean isStoppedEos() {
		return this.isStoppedEos;
	}
	
	public void setStoppedEos(final Boolean isStoppedEos) {
		this.isStoppedEos = isStoppedEos;
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
		builder.append(", content=");
		builder.append(this.content);
		builder.append(", isStoppedEos=");
		builder.append(this.isStoppedEos);
		builder.append(", usage=");
		builder.append(this.usage);
		builder.append("]");
		return builder.toString();
	}
}
