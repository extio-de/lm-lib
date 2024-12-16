package de.extio.lmlib.agent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.extio.lmlib.client.Completion;

public class JsonAgentResponseHandler implements AgentResponseHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonAgentResponseHandler.class);
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	
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
				final var items = new HashSet<>();
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
