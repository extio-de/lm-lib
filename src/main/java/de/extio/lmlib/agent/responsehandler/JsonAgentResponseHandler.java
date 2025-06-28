package de.extio.lmlib.agent.responsehandler;

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

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;

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
	public boolean handle(final Completion completion, final AgentContext context) {
		final int firstCurly = completion.response().indexOf("{");
		final int lastCurly = completion.response().lastIndexOf("}");
		final var response = completion.response().substring(firstCurly, lastCurly + 1);
		JsonNode jsonNode;
		try {
			jsonNode = this.objectMapper.readTree(response);
		}
		catch (final Exception e) {
			LOGGER.warn("Cannot parse json response: {}", completion.response());
			addJsonResponseErrorPrompt(context);
			return false;
		}
		
		boolean hasResponse = false;
		final Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
		while (fields.hasNext()) {
			final var entry = fields.next();
			if (entry.getValue().isArray()) {
				final var items = new LinkedHashSet<>();
				entry.getValue().elements().forEachRemaining(item -> items.add(item.asText()));
				context.getContext().put(entry.getKey(), List.copyOf(items));
				hasResponse = true;
				LOGGER.debug("Response: {} {}", entry.getKey(), items);
			}
			else if (entry.getValue().isTextual() || entry.getValue().isNumber() || entry.getValue().isBoolean()) {
				context.setStringValue(entry.getKey(), entry.getValue().asText());
				hasResponse = true;
				LOGGER.debug("Response: {} {}", entry.getKey(), entry.getValue().asText());
			}
		}
		
		if (!hasResponse) {
			addJsonResponseErrorPrompt(context);
		}
		return hasResponse;
	}

	private void addJsonResponseErrorPrompt(final AgentContext context) {
		final var turn = context.getConversation().getConversation().getLast();
		context.getConversation().replaceTurn(new Conversation.Turn(turn.type(), turn.text() + "\n\nThe previous response could not be fully processed or validated. Please make sure to format the response in valid JSON syntax with properly escaped characters."));
	}
	
}
