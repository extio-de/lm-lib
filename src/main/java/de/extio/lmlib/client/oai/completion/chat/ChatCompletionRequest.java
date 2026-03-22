package de.extio.lmlib.client.oai.completion.chat;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.*;

import de.extio.lmlib.client.oai.completion.AbstractCompletionRequest;

final class ChatCompletionRequest extends AbstractCompletionRequest {
	
	private List<ChatMessage> messages;
	
	private Integer maxCompletionTokens;

	private ChatCompletionsRequestReasoning reasoning;

	private List<ChatCompletionTool> tools;

	private Object toolChoice;

	private Boolean parallelToolCalls;
	
	@JsonProperty("messages")
	public List<ChatMessage> getMessages() {
		return this.messages;
	}
	
	public void setMessages(final List<ChatMessage> messages) {
		this.messages = messages;
	}
	
	@JsonProperty("max_completion_tokens")
	public Integer getMaxCompletionTokens() {
		return this.maxCompletionTokens;
	}
	
	public void setMaxCompletionTokens(final Integer maxCompletionTokens) {
		this.maxCompletionTokens = maxCompletionTokens;
	}
	
	@JsonProperty("reasoning")
	public ChatCompletionsRequestReasoning getReasoning() {
		return this.reasoning;
	}

	public void setReasoning(final ChatCompletionsRequestReasoning reasoning) {
		this.reasoning = reasoning;
	}

	@JsonProperty("tools")
	public List<ChatCompletionTool> getTools() {
		return this.tools;
	}

	public void setTools(final List<ChatCompletionTool> tools) {
		this.tools = tools;
	}

	@JsonProperty("tool_choice")
	public Object getToolChoice() {
		return this.toolChoice;
	}

	public void setToolChoice(final Object toolChoice) {
		this.toolChoice = toolChoice;
	}

	@JsonProperty("parallel_tool_calls")
	public Boolean getParallelToolCalls() {
		return this.parallelToolCalls;
	}

	public void setParallelToolCalls(final Boolean parallelToolCalls) {
		this.parallelToolCalls = parallelToolCalls;
	}

	final static class ChatCompletionsRequestReasoning {

		private ChatCompletionsRequestReasoningEffort effort;

		private String summary;

		@JsonProperty("effort")
		public ChatCompletionsRequestReasoningEffort getEffort() {
			return this.effort;
		}

		public void setEffort(final ChatCompletionsRequestReasoningEffort effort) {
			this.effort = effort;
		}

		@JsonProperty("summary")
		public String getSummary() {
			return this.summary;
		}

		public void setSummary(final String summary) {
			this.summary = summary;
		}

	}

	static enum ChatCompletionsRequestReasoningEffort {
		minimal, low, medium, high
	}

	static enum ChatCompletionsRequestReasoningSummaryDetails {
		auto, concise, detailed
	}

	static final class ChatCompletionTool {

		private String type;

		private ChatCompletionFunction function;

		@JsonProperty("type")
		public String getType() {
			return this.type;
		}

		public void setType(final String type) {
			this.type = type;
		}

		@JsonProperty("function")
		public ChatCompletionFunction getFunction() {
			return this.function;
		}

		public void setFunction(final ChatCompletionFunction function) {
			this.function = function;
		}

	}

	static final class ChatCompletionFunction {

		private String name;

		private String description;

		private Map<String, Object> parameters;

		private Boolean strict;

		@JsonProperty("name")
		public String getName() {
			return this.name;
		}

		public void setName(final String name) {
			this.name = name;
		}

		@JsonProperty("description")
		public String getDescription() {
			return this.description;
		}

		public void setDescription(final String description) {
			this.description = description;
		}

		@JsonProperty("parameters")
		public Map<String, Object> getParameters() {
			return this.parameters;
		}

		public void setParameters(final Map<String, Object> parameters) {
			this.parameters = parameters;
		}

		@JsonProperty("strict")
		public Boolean getStrict() {
			return this.strict;
		}

		public void setStrict(final Boolean strict) {
			this.strict = strict;
		}

	}

	static final class ChatCompletionNamedToolChoice {

		private String type;

		private ChatCompletionNamedToolChoiceFunction function;

		@JsonProperty("type")
		public String getType() {
			return this.type;
		}

		public void setType(final String type) {
			this.type = type;
		}

		@JsonProperty("function")
		public ChatCompletionNamedToolChoiceFunction getFunction() {
			return this.function;
		}

		public void setFunction(final ChatCompletionNamedToolChoiceFunction function) {
			this.function = function;
		}

	}

	static final class ChatCompletionNamedToolChoiceFunction {

		private String name;

		@JsonProperty("name")
		public String getName() {
			return this.name;
		}

		public void setName(final String name) {
			this.name = name;
		}

	}

}
