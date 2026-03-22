package de.extio.lmlib.client;

import java.util.Map;

public record ToolDefinition(String name, String description, Map<String, Object> parameters, Boolean strict) {

	public ToolDefinition {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Tool name cannot be null or blank");
		}
		parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
	}

	public ToolDefinition(final String name, final String description, final Map<String, Object> parameters) {
		this(name, description, parameters, null);
	}

	public ToolDefinition(final String name, final String description, final ToolParameters parameters, final Boolean strict) {
		this(name, description, parameters != null ? parameters.map() : null, strict);
	}

	public ToolDefinition(final String name, final String description, final ToolParameters parameters) {
		this(name, description, parameters != null ? parameters.map() : null, null);
	}

}