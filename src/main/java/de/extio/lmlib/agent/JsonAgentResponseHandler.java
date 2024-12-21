package de.extio.lmlib.agent;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.extio.lmlib.client.Completion;

public class JsonAgentResponseHandler implements AgentResponseHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonAgentResponseHandler.class);
	
	private final ObjectMapper objectMapper;
	
	public JsonAgentResponseHandler() {
		this.objectMapper = new ObjectMapper();
		this.objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
		this.objectMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
		this.objectMapper.configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, false);
		this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false);
		this.objectMapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
		this.objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
		this.objectMapper.configure(JsonParser.Feature.ALLOW_MISSING_VALUES, true);
		this.objectMapper.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
		this.objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
		this.objectMapper.configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true);
		this.objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
		this.objectMapper.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true);
		this.objectMapper.configure(JsonParser.Feature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS, true);
		this.objectMapper.configure(JsonParser.Feature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS, true);
		this.objectMapper.configure(JsonParser.Feature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS, true);
		this.objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
	}
	
	@Override
	public boolean handle(final Split split, final Completion completion) {
		final int firstCurly = completion.response().indexOf("{");
		final int lastCurly = completion.response().lastIndexOf("}");
		final var response = completion.response().substring(firstCurly, lastCurly + 1);
		JsonNode jsonNode;
		try {
			jsonNode = this.objectMapper.readTree(response);
		}
		catch (final Exception e) {
			LOGGER.warn("Cannot parse json response: {}", completion.response());
			return false;
		}
		
		boolean hasResponse = false;
		final Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
		while (fields.hasNext()) {
			final var entry = fields.next();
			if (entry.getValue().isArray()) {
				final var items = new LinkedHashSet<>();
				entry.getValue().elements().forEachRemaining(item -> items.add(item.asText()));
				split.context().getContext().put(entry.getKey(), List.copyOf(items));
				hasResponse = true;
				LOGGER.debug("Response: {} {}", entry.getKey(), items);
			}
			else if (entry.getValue().isTextual() || entry.getValue().isNumber() || entry.getValue().isBoolean()) {
				split.context().getContext().put(entry.getKey(), List.of(entry.getValue().asText()));
				hasResponse = true;
				LOGGER.debug("Response: {} {}", entry.getKey(), entry.getValue().asText());
			}
		}
		
		return hasResponse;
	}
	
}
