package de.extio.lmlib.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

public final class ToolParameters {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

	private ToolParameters() {
	}

	public static ToolParameters create() {
		return new ToolParameters();
	}

	public static ToolParameters create(final Map<String, String> properties) {
		final var schemaProperties = ToolParameters.create();
		for (final var entry : properties.entrySet()) {
			schemaProperties.add(entry.getKey(), ToolParameters.create()
					.add("type", "string")
					.add("description", entry.getValue()));
		}

		return new ToolParameters()
				.add("type", "object")
				.add("properties", schemaProperties)
				.add("required", List.copyOf(properties.keySet()))
				.add("additionalProperties", false);				
	}

	public ToolParameters add(final String key, final Object value) {
		this.values.put(key, this.normalize(value));
		return this;
	}

	public Map<String, Object> map() {
		return Map.copyOf(this.values);
	}

	public String json() {
		try {
			return OBJECT_MAPPER.writeValueAsString(this.values);
		}
		catch (final Exception e) {
			throw new IllegalStateException("Cannot serialize tool parameters", e);
		}
	}

	private Object normalize(final Object value) {
		return switch (value) {
			case null -> null;
			case final ToolParameters toolParameters -> toolParameters.map();
			case final Map<?, ?> map -> this.normalizeMap(map);
			case final Collection<?> collection -> this.normalizeCollection(collection);
			default -> value;
		};
	}

	private Map<String, Object> normalizeMap(final Map<?, ?> map) {
		final var normalized = new LinkedHashMap<String, Object>();
		for (final var entry : map.entrySet()) {
			normalized.put(String.valueOf(entry.getKey()), this.normalize(entry.getValue()));
		}
		return normalized;
	}

	private List<Object> normalizeCollection(final Collection<?> collection) {
		final var normalized = new ArrayList<Object>(collection.size());
		for (final var item : collection) {
			normalized.add(this.normalize(item));
		}
		return List.copyOf(normalized);
	}

}