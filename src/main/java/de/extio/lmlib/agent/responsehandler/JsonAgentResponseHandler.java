package de.extio.lmlib.agent.responsehandler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
				.enable(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)
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
			LOGGER.debug("Initial JSON parse failed, attempting to sanitize: {}", e.getMessage());
			try {
				final var response = completion.response().substring(firstCurly, lastCurly + 1);
				final var sanitized = sanitizeJson(response);
				jsonNode = this.objectMapper.readTree(sanitized);
				LOGGER.debug("Successfully parsed after sanitization");
			}
			catch (final Exception e2) {
				LOGGER.warn("Cannot parse json response even after sanitization: {}", completion.response());
				addJsonResponseErrorPrompt(context);
				return false;
			}
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
			entry.getValue().values().forEach(nestedArrayValue -> {
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
			for (final var entry : nestedProperty.getValue().properties()) {
				this.parseProperty(items, entry);
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
	
	private String sanitizeJson(final String json) {
		final var result = new StringBuilder(json.length() + 10);
		boolean inString = false;
		boolean escaped = false;
		
		for (int i = 0; i < json.length(); i++) {
			final char c = json.charAt(i);
			
			if (escaped) {
				result.append(c);
				escaped = false;
				continue;
			}
			
			if (c == '\\' && inString) {
				escaped = true;
				result.append(c);
				continue;
			}
			
			if (c == '"') {
				boolean unescapedQuote = false;
				if (inString) {
					for (int j = i + 1; j < json.length(); j++) {
						final var nextChar = json.charAt(j);
						if (Character.isWhitespace(nextChar)) {
							continue;
						}
						if (nextChar == ':' || nextChar == ',' || nextChar == '}' || nextChar == ']') {
							break;
						}
						result.append('\\');
						unescapedQuote = true;
						break;
					}
				}
				
				inString = unescapedQuote || !inString;
				result.append(c);
				continue;
			}
			
			if (inString && (c == '}' || c == ']') && (i == 0 || json.charAt(i - 1) != '\\')) {
				int insertPos = result.length();
				while (insertPos > 0 && Character.isWhitespace(result.charAt(insertPos - 1))) {
					insertPos--;
				}
				result.insert(insertPos, '"');
				inString = false;
			}
			
			result.append(c);
		}
		
		return result.toString();
	}
	
}
