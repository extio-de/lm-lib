package de.extio.lmlib.agent.responsehandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;

public class CsvUnmarshallingAgentResponseHandlerTest {
    
    public static record TestObject(String name, List<String> tags, Integer value) {
    }
    
    @Test
    public void testSimpleCsvParsing() {
        final CsvUnmarshallingAgentResponseHandler handler = new CsvUnmarshallingAgentResponseHandler(TestObject.class, "_test_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                item1;tag1,tag2;42
                item2;tag3;100
                item3;;50
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        final List<TestObject> objects = context.getValues("_test_", TestObject.class);
        assertEquals(3, objects.size());
        
        assertEquals("item1", objects.get(0).name());
        assertEquals(List.of("tag1", "tag2"), objects.get(0).tags());
        assertEquals(42, objects.get(0).value());
        
        assertEquals("item2", objects.get(1).name());
        assertEquals(List.of("tag3"), objects.get(1).tags());
        assertEquals(100, objects.get(1).value());
        
        assertEquals("item3", objects.get(2).name());
        assertTrue(objects.get(2).tags().isEmpty());
        assertEquals(50, objects.get(2).value());
    }
    
    @Test
    public void testCsvWithHeader() {
        final CsvUnmarshallingAgentResponseHandler handler = new CsvUnmarshallingAgentResponseHandler(TestObject.class, "_test_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                name;tags;value
                item1;tag1,tag2;42
                item2;tag3;100
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        final List<TestObject> objects = context.getValues("_test_", TestObject.class);
        assertEquals(2, objects.size());
        
        assertEquals("item1", objects.get(0).name());
        assertEquals("item2", objects.get(1).name());
    }
    
    @Test
    public void testCsvWithPreamble() {
        final CsvUnmarshallingAgentResponseHandler handler = new CsvUnmarshallingAgentResponseHandler(TestObject.class, "_test_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                Here is the result:
                
                item1;tag1,tag2;42
                item2;tag3;100
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        final List<TestObject> objects = context.getValues("_test_", TestObject.class);
        assertEquals(2, objects.size());
    }
    
    @Test
    public void testCsvWithHeaderAndPreamble() {
        final CsvUnmarshallingAgentResponseHandler handler = new CsvUnmarshallingAgentResponseHandler(TestObject.class, "_test_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                Here are the results in CSV format:
                
                name;tags;value
                item1;tag1,tag2;42
                item2;tag3;100
                item3;;50
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        final List<TestObject> objects = context.getValues("_test_", TestObject.class);
        assertEquals(3, objects.size());
        
        assertEquals("item1", objects.get(0).name());
        assertEquals(List.of("tag1", "tag2"), objects.get(0).tags());
        assertEquals(42, objects.get(0).value());
    }
    
    @Test
    public void testCsvWithEmptyLines() {
        final CsvUnmarshallingAgentResponseHandler handler = new CsvUnmarshallingAgentResponseHandler(TestObject.class, "_test_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                
                item1;tag1,tag2;42
                
                item2;tag3;100
                
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        final List<TestObject> objects = context.getValues("_test_", TestObject.class);
        assertEquals(2, objects.size());
    }
    
    @Test
    public void testCsvWithNullValues() {
        final CsvUnmarshallingAgentResponseHandler handler = new CsvUnmarshallingAgentResponseHandler(TestObject.class, "_test_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                item1;tag1,tag2;
                item2;;100
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        final List<TestObject> objects = context.getValues("_test_", TestObject.class);
        assertEquals(2, objects.size());
        
        assertEquals("item1", objects.get(0).name());
        assertNotNull(objects.get(0).tags());
        assertEquals(null, objects.get(0).value());
        
        assertEquals("item2", objects.get(1).name());
        assertTrue(objects.get(1).tags().isEmpty());
        assertEquals(100, objects.get(1).value());
    }
    
    public static record TagObject(String tag, List<String> aliases, Integer importance) {
    }
    
    @Test
    public void testTagObjectExample() {
        final CsvUnmarshallingAgentResponseHandler handler = new CsvUnmarshallingAgentResponseHandler(TagObject.class, "_tag_");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                location_dark_forest;location_forest,location_shadow_woods;25
                location_ancient_shrine;location_shrine,location_vine_shrine;65
                combat_dragon;combat_wyrm,combat_ancient_dragon;65
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        final List<TagObject> objects = context.getValues("_tag_", TagObject.class);
        assertEquals(3, objects.size());
        
        assertEquals("location_dark_forest", objects.get(0).tag());
        assertEquals(List.of("location_forest", "location_shadow_woods"), objects.get(0).aliases());
        assertEquals(25, objects.get(0).importance());
        
        assertEquals("combat_dragon", objects.get(2).tag());
        assertEquals(List.of("combat_wyrm", "combat_ancient_dragon"), objects.get(2).aliases());
        assertEquals(65, objects.get(2).importance());
    }
    
    @Test
    public void testCustomSeparators() {
        final CsvUnmarshallingAgentResponseHandler handler = new CsvUnmarshallingAgentResponseHandler(
                TagObject.class, "_custom_", "|", ":");
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                location_dark_forest|location_forest:location_shadow_woods|25
                location_ancient_shrine|location_shrine:location_vine_shrine|65
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        final List<TagObject> objects = context.getValues("_custom_", TagObject.class);
        assertEquals(2, objects.size());
        
        assertEquals("location_dark_forest", objects.get(0).tag());
        assertEquals(List.of("location_forest", "location_shadow_woods"), objects.get(0).aliases());
        assertEquals(25, objects.get(0).importance());
    }
    
    @Test
    public void testNoListSeparator() {
        final CsvUnmarshallingAgentResponseHandler handler = new CsvUnmarshallingAgentResponseHandler(
                TestObject.class, "_nolist_", ";", null);
        final AgentContext context = new AgentContext();
        context.setConversation(Conversation.create("test"));
        
        final String csvResponse = """
                item1;tag1,tag2,tag3;42
                item2;ignored;100
                """;
        
        final Completion completion = new Completion(csvResponse, null, null, null);
        final boolean result = handler.handle(completion, context);
        
        assertTrue(result);
        
        final List<TestObject> objects = context.getValues("_nolist_", TestObject.class);
        assertEquals(2, objects.size());
        
        assertEquals("item1", objects.get(0).name());
        assertTrue(objects.get(0).tags().isEmpty());
        assertEquals(42, objects.get(0).value());
        
        assertEquals("item2", objects.get(1).name());
        assertTrue(objects.get(1).tags().isEmpty());
        assertEquals(100, objects.get(1).value());
    }
}
