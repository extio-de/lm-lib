package de.extio.lmlib.agent.responsehandler;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;

public class JsonUnmarshallingAgentResponseHandler implements StreamedAgentResponseHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonUnmarshallingAgentResponseHandler.class);
	
	private final Class<?> targetClass;
	
	private final String contextKey;
	
	private final String reasoningKey;
	
	private final String errorPrompt;
	
	private final BiConsumer<AgentContext, Chunk> afterChunkUpdate;
	
	public JsonUnmarshallingAgentResponseHandler(final Class<?> targetClass, final String contextKey) {
		this(targetClass, contextKey, null);
	}
	
	public JsonUnmarshallingAgentResponseHandler(final Class<?> targetClass, final String contextKey, final BiConsumer<AgentContext, Chunk> afterChunkUpdate) {
		if (targetClass == null) {
			throw new IllegalArgumentException("Target class cannot be null");
		}
		if (contextKey == null || contextKey.isBlank()) {
			throw new IllegalArgumentException("Context key cannot be null or blank");
		}
		this.targetClass = targetClass;
		this.contextKey = contextKey;
		this.reasoningKey = contextKey + "_reasoning";
		this.afterChunkUpdate = afterChunkUpdate;
		
		this.errorPrompt = "\n\nThe previous response could not be fully processed or validated. " +
				"Please format the response as a valid JSON object, or an array of JSON objects, " +
				"matching this structure: " + buildSchemaDescription(targetClass) + ".";
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
		final String resp = completion.response();
		if (resp == null || resp.isBlank()) {
			LOGGER.warn("Empty completion response for JSON unmarshalling");
			return true;
		}
		
		try {
			final JsonNode jsonNode = JsonSupport.parseStructure(resp);
			if (jsonNode == null) {
				LOGGER.warn("Cannot parse json response even after sanitization: {}", resp);
				this.addJsonResponseErrorPrompt(context, resp);
				return false;
			}
			
			if (jsonNode.isArray()) {
				final List<Object> objects = new ArrayList<>();
				for (final JsonNode element : jsonNode) {
					objects.add(JsonSupport.MAPPER.treeToValue(element, this.targetClass));
				}
				if (objects.isEmpty()) {
					LOGGER.warn("Empty JSON array parsed from response");
					this.addJsonResponseErrorPrompt(context, resp);
					return false;
				}
				context.setValues(this.contextKey, objects);
				LOGGER.debug("Parsed {} JSON objects into context key '{}'", objects.size(), this.contextKey);
			}
			else {
				final Object obj = JsonSupport.MAPPER.treeToValue(jsonNode, this.targetClass);
				context.setValue(this.contextKey, obj);
				LOGGER.debug("Parsed JSON object into context key '{}': {}", this.contextKey, obj);
			}
			
			return true;
		}
		catch (final Exception ex) {
			LOGGER.warn("Failed to unmarshal JSON response", ex);
			this.addJsonResponseErrorPrompt(context, resp);
			return false;
		}
	}
	
	private void addJsonResponseErrorPrompt(final AgentContext context, final String jsonResponse) {
		final var conversation = context.getConversation();
		if (conversation == null) {
			return;
		}
		conversation.addTurn(new Conversation.Turn(
				Conversation.TurnType.ASSISTANT,
				jsonResponse != null ? jsonResponse : ""));
		conversation.addTurn(new Conversation.Turn(
				Conversation.TurnType.USER,
				this.errorPrompt));
	}
	
	private static String buildSchemaDescription(final Class<?> targetClass) {
		if (targetClass.isRecord()) {
			return Arrays.stream(targetClass.getRecordComponents())
					.map(component -> component.getName() + " (" + simpleTypeName(component.getGenericType()) + ")")
					.collect(Collectors.joining(", "));
		}
		return Arrays.stream(targetClass.getDeclaredFields())
				.filter(field -> !field.isSynthetic() && !Modifier.isStatic(field.getModifiers()))
				.map(field -> field.getName() + " (" + simpleTypeName(field.getGenericType()) + ")")
				.collect(Collectors.joining(", "));
	}
	
	private static String simpleTypeName(final Type type) {
		return type.getTypeName().replaceAll("(java\\.lang\\.|java\\.util\\.)", "");
	}
	
}
