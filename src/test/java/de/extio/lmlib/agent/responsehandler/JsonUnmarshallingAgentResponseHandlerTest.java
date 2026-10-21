package de.extio.lmlib.agent.responsehandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;

public class JsonUnmarshallingAgentResponseHandlerTest {
	
	public static record TestObject(String name, List<String> tags, Integer value) {
	}
	
	@Test
	public void testSingleObjectParsing() {
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "test");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{
					"name": "item1",
					"tags": ["tag1", "tag2"],
					"value": 42
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		
		final TestObject object = context.getValue("test", TestObject.class);
		assertNotNull(object);
		assertEquals("item1", object.name());
		assertEquals(List.of("tag1", "tag2"), object.tags());
		assertEquals(42, object.value());
		
		final List<TestObject> objects = context.getValues("test", TestObject.class);
		assertEquals(1, objects.size());
	}
	
	@Test
	public void testArrayOfObjects() {
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "test");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				[
					{"name": "item1", "tags": ["tag1", "tag2"], "value": 42},
					{"name": "item2", "tags": ["tag3"], "value": 100}
				]
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		
		final List<TestObject> objects = context.getValues("test", TestObject.class);
		assertEquals(2, objects.size());
		
		assertEquals("item1", objects.get(0).name());
		assertEquals(List.of("tag1", "tag2"), objects.get(0).tags());
		assertEquals(42, objects.get(0).value());
		
		assertEquals("item2", objects.get(1).name());
		assertEquals(List.of("tag3"), objects.get(1).tags());
		assertEquals(100, objects.get(1).value());
	}
	
	@Test
	public void testJsonWithPreamble() {
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "test");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				Here is the result:
				
				{
					"name": "item1",
					"tags": ["tag1"],
					"value": 42
				}
				
				Additional notes after the JSON.
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		assertEquals("item1", context.getValue("test", TestObject.class).name());
	}
	
	@Test
	public void testPreambleWithBraces() {
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "test");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				Use {braces} carefully in prose, then answer with JSON:
				{"name": "item1", "tags": ["tag1"], "value": 42}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		assertEquals("item1", context.getValue("test", TestObject.class).name());
	}
	
	@Test
	public void testStringCoercion() {
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "test");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{"name": "item1", "tags": ["tag1"], "value": "42"}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		assertEquals(42, context.getValue("test", TestObject.class).value());
	}
	
	@Test
	public void testLenientJsonFeatures() {
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "test");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{
					// This is a comment
					name: 'single quotes',
					tags: ['tag1', 'tag2'],
					value: 42,
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		final TestObject object = context.getValue("test", TestObject.class);
		assertEquals("single quotes", object.name());
		assertEquals(List.of("tag1", "tag2"), object.tags());
		assertEquals(42, object.value());
	}
	
	@Test
	public void testUnknownAndMissingProperties() {
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "test");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{"name": "item1", "unknownField": "ignored"}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		final TestObject object = context.getValue("test", TestObject.class);
		assertEquals("item1", object.name());
		assertNull(object.tags());
		assertNull(object.value());
	}
	
	@Test
	public void testSanitizeUnescapedQuotesInString() {
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "test");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{"name": ""yipii" said the clown", "tags": [], "value": 1}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		final String name = context.getValue("test", TestObject.class).name();
		assertNotNull(name);
		assertTrue(name.contains("yipii"));
		assertTrue(name.contains("clown"));
	}
	
	@Test
	public void testEmptyArrayReturnsFalse() {
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "test");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final Completion completion = new Completion("[]", null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertFalse(result);
		final List<Conversation.Turn> turns = context.getConversation().getConversation();
		final Conversation.Turn lastTurn = turns.get(turns.size() - 1);
		assertTrue(lastTurn.text().contains("The previous response could not be fully processed or validated."));
	}
	
	@Test
	public void testInvalidJsonCannotBeSanitized() {
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "test");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{
					this is completely broken [[[
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertFalse(result);
		
		final List<Conversation.Turn> turns = context.getConversation().getConversation();
		final Conversation.Turn lastTurn = turns.get(turns.size() - 1);
		assertTrue(lastTurn.text().contains("The previous response could not be fully processed or validated."));
	}
	
	@Test
	public void testErrorPromptContainsSchema() {
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "test");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final Completion completion = new Completion("no json here", null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertFalse(result);
		
		final List<Conversation.Turn> turns = context.getConversation().getConversation();
		final Conversation.Turn lastTurn = turns.get(turns.size() - 1);
		assertTrue(lastTurn.text().contains("name (String)"));
		assertTrue(lastTurn.text().contains("tags (List<String>)"));
		assertTrue(lastTurn.text().contains("value (Integer)"));
	}
	
	@Test
	public void testJsonWithNoBraces() {
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "test");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final Completion completion = new Completion("This is just plain text with no JSON", null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertFalse(result);
	}
	
	@Test
	public void testEmptyResponse() {
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "test");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final Completion completion = new Completion("", null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		assertNull(context.getValue("test", TestObject.class));
	}
	
	@Test
	public void testReasoningStreaming() {
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "stream");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		context.setStringValue("stream_reasoning", "stale");
		
		handler.beforeStream(context);
		assertEquals(null, context.getStringValue("stream_reasoning"));
		
		assertTrue(handler.handleChunk(new Chunk(null, "first"), context));
		assertEquals("first", context.getStringValue("stream_reasoning"));
		
		assertFalse(handler.handleChunk(new Chunk("{\"field\":\"value\"}", null), context));
		assertEquals("first", context.getStringValue("stream_reasoning"));
		
		assertTrue(handler.handleChunk(new Chunk(null, " second"), context));
		assertEquals("first second", context.getStringValue("stream_reasoning"));
	}
	
	@Test
	public void testAfterChunkUpdateCallback() {
		final List<String> callbackReasoning = new ArrayList<>();
		final List<String> callbackContent = new ArrayList<>();
		final JsonUnmarshallingAgentResponseHandler handler = new JsonUnmarshallingAgentResponseHandler(TestObject.class, "stream",
				(context, chunk) -> {
					callbackReasoning.add(context.getStringValue("stream_reasoning"));
					callbackContent.add(chunk.content());
				});
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		handler.beforeStream(context);
		
		assertTrue(handler.handleChunk(new Chunk(null, "first"), context));
		assertEquals("first", context.getStringValue("stream_reasoning"));
		assertEquals(1, callbackReasoning.size());
		assertEquals("first", callbackReasoning.getFirst());
		assertEquals(null, callbackContent.getFirst());
		
		assertTrue(handler.handleChunk(new Chunk("{\"field\":\"value\"}", null), context));
		assertEquals("first", context.getStringValue("stream_reasoning"));
		assertEquals(2, callbackReasoning.size());
		assertEquals("first", callbackReasoning.get(1));
		assertEquals("{\"field\":\"value\"}", callbackContent.get(1));
	}
	
}
