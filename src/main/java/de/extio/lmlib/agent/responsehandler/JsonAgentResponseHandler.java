package de.extio.lmlib.agent.responsehandler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;

public class JsonAgentResponseHandler implements StreamedAgentResponseHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonAgentResponseHandler.class);
	
	private final ObjectMapper objectMapper;
	
	private final String prefixFields;

	private final String reasoningKey;

	private final BiConsumer<AgentContext, Chunk> afterChunkUpdate;
	
	public JsonAgentResponseHandler() {
		this(null, null);
	}
	
	public JsonAgentResponseHandler(final String prefixFields) {
		this(prefixFields, null);
	}

	public JsonAgentResponseHandler(final String prefixFields, final BiConsumer<AgentContext, Chunk> afterChunkUpdate) {
		this.prefixFields = prefixFields == null ? "" : prefixFields;
		this.reasoningKey = this.prefixFields + "_reasoning";
		this.afterChunkUpdate = afterChunkUpdate;
		
		this.objectMapper = JsonSupport.MAPPER;
	}

	@Override
	public void beforeStream(final AgentContext context) {
		context.getContext().remove(this.reasoningKey);
	}

	@Override
	public boolean handleChunk(final Chunk chunk, final AgentContext context) {
		boolean handled = false;
		if (chunk.reasoningContent() != null) {
			final var existingReasoning = context.getStringValue(this.reasoningKey);
			context.setStringValue(this.reasoningKey, (existingReasoning == null ? "" : existingReasoning).concat(chunk.reasoningContent()));
			handled = true;
		}
		if (this.afterChunkUpdate != null) {
			this.afterChunkUpdate.accept(context, chunk);
			return true;
		}
		return handled;
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
				final var sanitized = JsonSupport.sanitizeJson(response);
				jsonNode = this.objectMapper.readTree(sanitized);
				LOGGER.debug("Successfully parsed after sanitization");
			}
			catch (final Exception e2) {
				LOGGER.warn("Cannot parse json response even after sanitization: {}", completion.response());
				addJsonResponseErrorPrompt(context, completion.response());
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
	
	private void addJsonResponseErrorPrompt(final AgentContext context, final String jsonResponse) {
		// final var turn = context.getConversation().getConversation().getLast();
		// context.getConversation().replaceTurn(new Conversation.Turn(turn.type(), turn.text() + "\n\nThe previous response could not be fully processed or validated. Please make sure to format the response in valid JSON structure with properly escaped characters."));
		context.getConversation().addTurn(new Conversation.Turn(
			Conversation.TurnType.ASSISTANT,
			jsonResponse != null ? jsonResponse : ""));
		context.getConversation().addTurn(new Conversation.Turn(
				Conversation.TurnType.USER,
				"The previous response could not be fully processed or validated. Please make sure to format the response in valid JSON structure with properly escaped characters."));
	}
	
}
