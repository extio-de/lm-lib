package de.extio.lmlib.client;

import java.util.Collection;
import java.util.List;

public record ToolCallData(List<ToolDefinition> tools, String toolChoice, String forcedToolName, Boolean parallelToolCalls) {

	public static final String TOOL_CHOICE_AUTO = "auto";

	public static final String TOOL_CHOICE_REQUIRED = "required";

	public static final String TOOL_CHOICE_NONE = "none";

	public ToolCallData {
		tools = tools == null ? List.of() : List.copyOf(tools);
	}

	public static ToolCallData auto(final Collection<ToolDefinition> tools) {
		return new ToolCallData(List.copyOf(tools), TOOL_CHOICE_AUTO, null, null);
	}

	public static ToolCallData required(final Collection<ToolDefinition> tools) {
		return new ToolCallData(List.copyOf(tools), TOOL_CHOICE_REQUIRED, null, null);
	}

	public static ToolCallData force(final Collection<ToolDefinition> tools, final String forcedToolName) {
		return new ToolCallData(List.copyOf(tools), null, forcedToolName, null);
	}

	public boolean hasTools() {
		return !this.tools.isEmpty();
	}

}