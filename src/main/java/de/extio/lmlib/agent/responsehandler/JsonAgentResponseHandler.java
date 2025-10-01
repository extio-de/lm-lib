package de.extio.lmlib.agent.responsehandler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;

public class JsonAgentResponseHandler implements AgentResponseHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonAgentResponseHandler.class);
	
	private final ObjectMapper objectMapper;
	
	private final String prefixFields;
	
	public JsonAgentResponseHandler() {
		this(null);
	}
	
	public JsonAgentResponseHandler(final String prefixFields) {
		this.prefixFields = prefixFields == null ? "" : prefixFields;
		
		this.objectMapper = JsonMapper.builder()
				.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
				.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
				.configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, false)
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false)
				.enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
				.enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
				.enable(JsonReadFeature.ALLOW_MISSING_VALUES)
				.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
				.enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
				.enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
				.enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
				.enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
				.enable(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
				.enable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
				.enable(JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)
				.enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
				.build();
	}
	
	@Override
	public boolean handle(final Completion completion, final AgentContext context) {
		final int firstCurly = completion.response().indexOf("{");
		final int lastCurly = completion.response().lastIndexOf("}");
		JsonNode jsonNode;
		try {
			final var response = completion.response().substring(firstCurly, lastCurly + 1);
			jsonNode = this.objectMapper.readTree(response);
		}
		catch (final Exception e) {
			LOGGER.warn("Cannot parse json response: {}", completion.response());
			addJsonResponseErrorPrompt(context);
			return false;
		}
		
		final var items = new LinkedHashMap<String, List<String>>();
		final Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.properties().iterator();
		while (fields.hasNext()) {
			final var entry = fields.next();
			parseEntry(items, entry);
		}
		LOGGER.debug("Response: {} {}", items.size(), items);
		context.getContext().putAll(items);
		
		return true;
	}
	
	private void parseEntry(final LinkedHashMap<String, List<String>> items, final Entry<String, JsonNode> entry) {
		if (entry.getValue().isArray()) {
			entry.getValue().elements().forEachRemaining(nestedArrayValue -> {
				if (nestedArrayValue.properties().isEmpty()) {
					items.computeIfAbsent(this.prefixFields + entry.getKey(), k -> new ArrayList<>()).add(nestedArrayValue.asText());
				}
				else {
					for (final var nestedProperty : nestedArrayValue.properties()) {
						this.parseEntry(items, nestedProperty);
					}
				}
			});
		}
		else {
			this.parseProperty(items, entry);
		}
	}
	
	private void parseProperty(final LinkedHashMap<String, List<String>> items, final Entry<String, JsonNode> nestedProperty) {
		if (nestedProperty.getValue().isObject()) {
			final var nestedValuesIter = nestedProperty.getValue().values();
			while (nestedValuesIter.hasNext()) {
				final var v = nestedValuesIter.next();
				if (v.isTextual() || v.isNumber() || v.isBoolean()) {
					items.computeIfAbsent(this.prefixFields + nestedProperty.getKey(), k -> new ArrayList<>()).add(v.asText());
				}
			}
		}
		else {
			items.computeIfAbsent(this.prefixFields + nestedProperty.getKey(), k -> new ArrayList<>()).add(nestedProperty.getValue().asText());
		}
	}
	
	private void addJsonResponseErrorPrompt(final AgentContext context) {
		final var turn = context.getConversation().getConversation().getLast();
		context.getConversation().replaceTurn(new Conversation.Turn(turn.type(), turn.text() + "\n\nThe previous response could not be fully processed or validated. Please make sure to format the response in valid JSON syntax with properly escaped characters."));
	}
	
}
