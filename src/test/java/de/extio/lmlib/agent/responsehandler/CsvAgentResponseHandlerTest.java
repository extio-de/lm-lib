package de.extio.lmlib.agent.responsehandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;

public class CsvAgentResponseHandlerTest {
    
    @Test
    public void testSimpleCsvParsing() {
        final CsvAgentResponseHandler handler = new CsvAgentResponseHandler(List.of("a", "b", "c"), "xy_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                1;2;3
                4;5;6
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        final List<String> aValues = context.getStringValues("xy_a");
        final List<String> bValues = context.getStringValues("xy_b");
        final List<String> cValues = context.getStringValues("xy_c");
        
        assertNotNull(aValues);
        assertNotNull(bValues);
        assertNotNull(cValues);
        
        assertEquals(List.of("1", "4"), aValues);
        assertEquals(List.of("2", "5"), bValues);
        assertEquals(List.of("3", "6"), cValues);
    }
    
    @Test
    public void testWithHeader() {
        final CsvAgentResponseHandler handler = new CsvAgentResponseHandler(List.of("name", "age", "city"), "person_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                name;age;city
                Alice;30;Berlin
                Bob;25;Munich
                Charlie;35;Hamburg
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        final List<String> names = context.getStringValues("person_name");
        final List<String> ages = context.getStringValues("person_age");
        final List<String> cities = context.getStringValues("person_city");
        
        assertEquals(List.of("Alice", "Bob", "Charlie"), names);
        assertEquals(List.of("30", "25", "35"), ages);
        assertEquals(List.of("Berlin", "Munich", "Hamburg"), cities);
    }
    
    @Test
    public void testWithEmptyPrefix() {
        final CsvAgentResponseHandler handler = new CsvAgentResponseHandler(List.of("x", "y"), "");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                10;20
                30;40
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        assertEquals(List.of("10", "30"), context.getStringValues("x"));
        assertEquals(List.of("20", "40"), context.getStringValues("y"));
    }
    
    @Test
    public void testWithNullPrefix() {
        final CsvAgentResponseHandler handler = new CsvAgentResponseHandler(List.of("col1", "col2"), null);
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                a;b
                c;d
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        assertEquals(List.of("a", "c"), context.getStringValues("col1"));
        assertEquals(List.of("b", "d"), context.getStringValues("col2"));
    }
    
    @Test
    public void testWithExtraTextBeforeData() {
        final CsvAgentResponseHandler handler = new CsvAgentResponseHandler(List.of("a", "b", "c"), "test_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                Here is the CSV data:
                
                a;b;c
                1;2;3
                4;5;6
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        assertEquals(List.of("1", "4"), context.getStringValues("test_a"));
        assertEquals(List.of("2", "5"), context.getStringValues("test_b"));
        assertEquals(List.of("3", "6"), context.getStringValues("test_c"));
    }
    
    @Test
    public void testWithEmptyLines() {
        final CsvAgentResponseHandler handler = new CsvAgentResponseHandler(List.of("a", "b"), "data_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                1;2
                
                3;4
                
                5;6
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        assertEquals(List.of("1", "3", "5"), context.getStringValues("data_a"));
        assertEquals(List.of("2", "4", "6"), context.getStringValues("data_b"));
    }
    
    @Test
    public void testWithWhitespace() {
        final CsvAgentResponseHandler handler = new CsvAgentResponseHandler(List.of("a", "b", "c"), "ws_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                  1  ;  2  ;  3  
                 4 ; 5 ; 6 
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        assertEquals(List.of("1", "4"), context.getStringValues("ws_a"));
        assertEquals(List.of("2", "5"), context.getStringValues("ws_b"));
        assertEquals(List.of("3", "6"), context.getStringValues("ws_c"));
    }
    
    @Test
    public void testWithCustomSeparator() {
        final CsvAgentResponseHandler handler = new CsvAgentResponseHandler(List.of("a", "b", "c"), "custom_", ",");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                1,2,3
                4,5,6
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        assertEquals(List.of("1", "4"), context.getStringValues("custom_a"));
        assertEquals(List.of("2", "5"), context.getStringValues("custom_b"));
        assertEquals(List.of("3", "6"), context.getStringValues("custom_c"));
    }
    
    @Test
    public void testColumnCountMismatch() {
        final CsvAgentResponseHandler handler = new CsvAgentResponseHandler(List.of("a", "b", "c"), "mismatch_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                1;2;3
                4;5
                7;8;9
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        // Only rows with correct column count should be included
        assertEquals(List.of("1", "7"), context.getStringValues("mismatch_a"));
        assertEquals(List.of("2", "8"), context.getStringValues("mismatch_b"));
        assertEquals(List.of("3", "9"), context.getStringValues("mismatch_c"));
    }
    
    @Test
    public void testEmptyResponse() {
        final CsvAgentResponseHandler handler = new CsvAgentResponseHandler(List.of("a", "b"), "empty_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final Completion completion = new Completion("", null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertFalse(result);
    }
    
    @Test
    public void testNullResponse() {
        final CsvAgentResponseHandler handler = new CsvAgentResponseHandler(List.of("a", "b"), "null_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final Completion completion = new Completion(null, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertFalse(result);
    }
    
    @Test
    public void testNoValidData() {
        final CsvAgentResponseHandler handler = new CsvAgentResponseHandler(List.of("a", "b"), "nodata_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                This is just text
                without any valid CSV data
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertFalse(result);
    }
    
    @Test
    public void testSingleRow() {
        final CsvAgentResponseHandler handler = new CsvAgentResponseHandler(List.of("x", "y", "z"), "single_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = "100;200;300";
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        assertEquals(List.of("100"), context.getStringValues("single_x"));
        assertEquals(List.of("200"), context.getStringValues("single_y"));
        assertEquals(List.of("300"), context.getStringValues("single_z"));
    }
    
    @Test
    public void testMultipleHeaders() {
        final CsvAgentResponseHandler handler = new CsvAgentResponseHandler(List.of("id", "value"), "multi_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                id;value
                ID;VALUE
                1;100
                2;200
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        // Should skip both header rows
        assertEquals(List.of("1", "2"), context.getStringValues("multi_id"));
        assertEquals(List.of("100", "200"), context.getStringValues("multi_value"));
    }
    
    @Test
    public void testNullHeadingsList() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CsvAgentResponseHandler(null, "prefix_");
        });
    }
    
    @Test
    public void testEmptyHeadingsList() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CsvAgentResponseHandler(List.of(), "prefix_");
        });
    }
}
