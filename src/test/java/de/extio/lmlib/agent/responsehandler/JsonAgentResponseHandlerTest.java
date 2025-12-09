package de.extio.lmlib.agent.responsehandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;

public class JsonAgentResponseHandlerTest {
	
	@Test
	public void testSimpleJsonParsing() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{
					"name": "John Doe",
					"age": "30",
					"city": "New York"
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		assertEquals("John Doe", context.getStringValue("name"));
		assertEquals("30", context.getStringValue("age"));
		assertEquals("New York", context.getStringValue("city"));
	}
	
	@Test
	public void testJsonWithPrefix() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler("test_");
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{
					"field1": "value1",
					"field2": "value2"
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		assertEquals("value1", context.getStringValue("test_field1"));
		assertEquals("value2", context.getStringValue("test_field2"));
	}
	
	@Test
	public void testJsonWithPreamble() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				Here is the result:
				
				{
					"status": "success",
					"message": "Operation completed"
				}
				
				Additional notes after the JSON.
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		assertEquals("success", context.getStringValue("status"));
		assertEquals("Operation completed", context.getStringValue("message"));
	}
	
	@Test
	public void testJsonArray() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{
					"items": ["apple", "banana", "cherry"]
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		final List<String> items = context.getStringValues("items");
		assertNotNull(items);
		assertEquals(3, items.size());
		assertEquals("apple", items.get(0));
		assertEquals("banana", items.get(1));
		assertEquals("cherry", items.get(2));
	}
	
	@Test
	public void testJsonNestedObject() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{
					"user": {
						"name": "Jane",
						"email": "jane@example.com"
					}
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		assertEquals("Jane", context.getStringValue("name"));
		assertEquals("jane@example.com", context.getStringValue("email"));
	}
	
	@Test
	public void testJsonArrayOfObjects() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{
					"users": [
						{"name": "Alice", "age": "25"},
						{"name": "Bob", "age": "30"}
					]
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		final List<String> names = context.getStringValues("name");
		final List<String> ages = context.getStringValues("age");
		assertNotNull(names);
		assertNotNull(ages);
		assertEquals(2, names.size());
		assertEquals(2, ages.size());
		assertEquals("Alice", names.get(0));
		assertEquals("Bob", names.get(1));
		assertEquals("25", ages.get(0));
		assertEquals("30", ages.get(1));
	}
	
	@Test
	public void testSanitizeMissingClosingQuote() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		// With lenient JSON parsing enabled, this should work
		final String jsonResponse = """
				{
					"field": "this is not right
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		assertEquals("this is not right", context.getStringValue("field"));
	}
	
	@Test
	public void testSanitizeUnescapedQuotesInString() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{
					"field": '"yipii" said the clown'
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		final String value = context.getStringValue("field");
		assertNotNull(value);
		assertTrue(value.contains("yipii"));
		assertTrue(value.contains("clown"));
	}

    @Test
	public void testSanitizeUnescapedQuotesInString2() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{
					"field": ""yipii" said the clown"
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		final String value = context.getStringValue("field");
		assertNotNull(value);
		assertTrue(value.contains("yipii"));
		assertTrue(value.contains("clown"));
	}
	
	@Test
	public void testLenientJsonFeatures() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{
					"greeting": 'Hello "World"',
					"message": 'She said "hi" to me',
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		assertNotNull(context.getStringValue("greeting"));
		assertNotNull(context.getStringValue("message"));
	}
	
	@Test
	public void testJsonWithEscapedCharacters() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{
					"path": "C:\\\\Users\\\\Documents",
					"quote": "He said \\"hello\\""
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		assertNotNull(context.getStringValue("path"));
		assertNotNull(context.getStringValue("quote"));
	}
	
	@Test
	public void testInvalidJsonCannotBeSanitized() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
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
		
		// Verify error message was added to conversation
		final List<Conversation.Turn> turns = context.getConversation().getConversation();
		final Conversation.Turn lastTurn = turns.get(turns.size() - 1);
		assertTrue(lastTurn.text().contains("valid JSON syntax"));
	}
	
	@Test
	public void testJsonWithNoCurlyBraces() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = "This is just plain text with no JSON";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertFalse(result);
	}
	
	@Test
	public void testJsonWithAllowedFeatures() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		// Test various lenient JSON features that should be accepted
		final String jsonResponse = """
				{
					// This is a comment
					field1: 'single quotes',
					field2: "trailing comma",
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		assertEquals("single quotes", context.getStringValue("field1"));
		assertEquals("trailing comma", context.getStringValue("field2"));
	}
	
	@Test
	public void testJsonWithNumbers() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = """
				{
					"count": 42,
					"price": 19.99,
					"active": true
				}
				""";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		assertEquals("42", context.getStringValue("count"));
		assertEquals("19.99", context.getStringValue("price"));
		assertEquals("true", context.getStringValue("active"));
	}
	
	@Test
	public void testEmptyJson() {
		final JsonAgentResponseHandler handler = new JsonAgentResponseHandler();
		final AgentContext context = new AgentContext();
		context.setConversation(Conversation.create("test"));
		
		final String jsonResponse = "{}";
		
		final Completion completion = new Completion(jsonResponse, null, null, null);
		final boolean result = handler.handle(completion, context);
		
		assertTrue(result);
		assertTrue(context.getContext().isEmpty());
	}
	
}
