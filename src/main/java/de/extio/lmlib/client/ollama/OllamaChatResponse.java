package de.extio.lmlib.client.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;

final class OllamaChatResponse {

	private String model;

	private String createdAt;

	private OllamaMessage message;

	private Boolean done;

	private String doneReason;

	private Long totalDuration;

	private Long loadDuration;

	private Integer promptEvalCount;

	private Long promptEvalDuration;

	private Integer evalCount;

	private Long evalDuration;

	private String error;

	@JsonProperty("model")
	public String getModel() {
		return this.model;
	}

	public void setModel(final String model) {
		this.model = model;
	}

	@JsonProperty("created_at")
	public String getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(final String createdAt) {
		this.createdAt = createdAt;
	}

	@JsonProperty("message")
	public OllamaMessage getMessage() {
		return this.message;
	}

	public void setMessage(final OllamaMessage message) {
		this.message = message;
	}

	@JsonProperty("done")
	public Boolean getDone() {
		return this.done;
	}

	public void setDone(final Boolean done) {
		this.done = done;
	}

	@JsonProperty("done_reason")
	public String getDoneReason() {
		return this.doneReason;
	}

	public void setDoneReason(final String doneReason) {
		this.doneReason = doneReason;
	}

	@JsonProperty("total_duration")
	public Long getTotalDuration() {
		return this.totalDuration;
	}

	public void setTotalDuration(final Long totalDuration) {
		this.totalDuration = totalDuration;
	}

	@JsonProperty("load_duration")
	public Long getLoadDuration() {
		return this.loadDuration;
	}

	public void setLoadDuration(final Long loadDuration) {
		this.loadDuration = loadDuration;
	}

	@JsonProperty("prompt_eval_count")
	public Integer getPromptEvalCount() {
		return this.promptEvalCount;
	}

	public void setPromptEvalCount(final Integer promptEvalCount) {
		this.promptEvalCount = promptEvalCount;
	}

	@JsonProperty("prompt_eval_duration")
	public Long getPromptEvalDuration() {
		return this.promptEvalDuration;
	}

	public void setPromptEvalDuration(final Long promptEvalDuration) {
		this.promptEvalDuration = promptEvalDuration;
	}

	@JsonProperty("eval_count")
	public Integer getEvalCount() {
		return this.evalCount;
	}

	public void setEvalCount(final Integer evalCount) {
		this.evalCount = evalCount;
	}

	@JsonProperty("eval_duration")
	public Long getEvalDuration() {
		return this.evalDuration;
	}

	public void setEvalDuration(final Long evalDuration) {
		this.evalDuration = evalDuration;
	}

	@JsonProperty("error")
	public String getError() {
		return this.error;
	}

	public void setError(final String error) {
		this.error = error;
	}

	void copyFrom(final OllamaChatResponse other) {
		if (other.model != null && !other.model.isBlank()) {
			this.model = other.model;
		}
		if (other.createdAt != null && !other.createdAt.isBlank()) {
			this.createdAt = other.createdAt;
		}
		if (other.done != null) {
			this.done = other.done;
		}
		if (other.doneReason != null && !other.doneReason.isBlank()) {
			this.doneReason = other.doneReason;
		}
		if (other.totalDuration != null) {
			this.totalDuration = other.totalDuration;
		}
		if (other.loadDuration != null) {
			this.loadDuration = other.loadDuration;
		}
		if (other.promptEvalCount != null) {
			this.promptEvalCount = other.promptEvalCount;
		}
		if (other.promptEvalDuration != null) {
			this.promptEvalDuration = other.promptEvalDuration;
		}
		if (other.evalCount != null) {
			this.evalCount = other.evalCount;
		}
		if (other.evalDuration != null) {
			this.evalDuration = other.evalDuration;
		}
		if (other.error != null && !other.error.isBlank()) {
			this.error = other.error;
		}
	}
}