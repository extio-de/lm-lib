package de.extio.lmlib.agent.responsehandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;

public class CsvAgentResponseHandler implements AgentResponseHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CsvAgentResponseHandler.class);
    
    private final Class<?> targetClass;
    
    private final String contextKey;
    
    private final String fieldSeparator;
    
    private final String listSeparator;
    
    private final Field[] fields;
    
    private final List<String> fieldNames;
    
    private final Constructor<?> constructor;
    
    private final String fieldSeparatorQuoted;
    
    private final String listSeparatorQuoted;
    
    private final String errorPromptSuffix;
    
    public CsvAgentResponseHandler(final Class<?> targetClass, final String contextKey) {
        this(targetClass, contextKey, ";", ",");
    }
    
    public CsvAgentResponseHandler(final Class<?> targetClass, final String contextKey,
            final String fieldSeparator, final String listSeparator) {
        this.targetClass = targetClass;
        this.contextKey = contextKey;
        this.fieldSeparator = fieldSeparator;
        this.listSeparator = listSeparator;
        
        // Pre-compute static data
        this.fields = targetClass.getDeclaredFields();
        this.fieldNames = Arrays.stream(this.fields)
                .map(Field::getName)
                .toList();
        this.constructor = this.findRecordConstructor();
        if (this.constructor == null) {
            throw new IllegalStateException("No suitable constructor found for " + targetClass.getName());
        }
        this.fieldSeparatorQuoted = Pattern.quote(fieldSeparator);
        this.listSeparatorQuoted = (listSeparator != null && !listSeparator.isEmpty())
                ? Pattern.quote(listSeparator)
                : null;
        
        // Pre-compute error message suffix
        final String listInfo = (listSeparator != null && !listSeparator.isEmpty())
                ? " and '" + listSeparator + "' for list items"
                : "";
        this.errorPromptSuffix = "\n\nThe previous response could not be fully processed or validated. " +
                "Please format the response as CSV with '" + fieldSeparator + "' as field separator" +
                listInfo + ". One object per line.";
    }
    
    private Constructor<?> findRecordConstructor() {
        final Field[] targetFields = this.targetClass.getDeclaredFields();
        for (final Constructor<?> ctor : this.targetClass.getDeclaredConstructors()) {
            if (ctor.getParameterCount() == targetFields.length) {
                return ctor;
            }
        }
        return null;
    }
    
    @Override
    public boolean handle(final Completion completion, final AgentContext context) {
        final String resp = completion.response();
        if (resp == null || resp.isBlank()) {
            LOGGER.warn("Empty completion response for CSV parsing");
            return false;
        }
        
        try {
            final List<Object> parsedObjects = this.parseCsv(resp);
            if (parsedObjects.isEmpty()) {
                LOGGER.warn("No valid CSV data parsed from response");
                this.addCsvResponseErrorPrompt(context);
                return false;
            }
            
            context.setValues(this.contextKey, parsedObjects);
            LOGGER.debug("Parsed {} CSV objects", parsedObjects.size());
            return true;
        }
        catch (final Exception ex) {
            LOGGER.warn("Failed to parse CSV response", ex);
            this.addCsvResponseErrorPrompt(context);
            return false;
        }
    }
    
    private List<Object> parseCsv(final String csvText) throws Exception {
        final List<Object> result = new ArrayList<>();
        final String[] lines = csvText.split("\n");
        
        int startLine = 0;
        
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i].trim();
            
            if (line.isEmpty()) {
                continue;
            }
            
            if (!line.contains(this.fieldSeparator)) {
                startLine = i + 1;
                continue;
            }
            
            final String lowerLine = line.toLowerCase();
            boolean isHeader = false;
            for (final String fieldName : this.fieldNames) {
                if (lowerLine.contains(fieldName.toLowerCase())) {
                    isHeader = true;
                    break;
                }
            }
            
            if (isHeader) {
                startLine = i + 1;
                continue;
            }
            
            break;
        }
        
        for (int i = startLine; i < lines.length; i++) {
            final String line = lines[i].trim();
            
            if (line.isEmpty()) {
                continue;
            }
            
            if (!line.contains(this.fieldSeparator)) {
                continue;
            }
            
            try {
                final Object obj = this.parseLine(line);
                if (obj != null) {
                    result.add(obj);
                }
            }
            catch (final Exception ex) {
                LOGGER.debug("Failed to parse line '{}': {}", line, ex.getMessage());
            }
        }
        
        return result;
    }
    
    private Object parseLine(final String line) throws Exception {
        final String[] parts = line.split(this.fieldSeparatorQuoted, -1);
        if (parts.length != this.fields.length) {
            LOGGER.debug("Field count mismatch: expected {}, got {} for line '{}'",
                    this.fields.length, parts.length, line);
            return null;
        }
        
        final Object[] args = new Object[this.fields.length];
        
        for (int i = 0; i < this.fields.length; i++) {
            final String value = parts[i].trim();
            args[i] = this.parseValue(value, this.fields[i]);
        }
        
        return this.constructor.newInstance(args);
    }
    
    private Object parseValue(final String value, final Field field) {
        final Class<?> fieldType = field.getType();
        
        if (value.isEmpty() || value.equalsIgnoreCase("null")) {
            if (fieldType == List.class) {
                return new ArrayList<>();
            }
            return null;
        }
        
        if (fieldType == String.class) {
            return value;
        }
        
        if (fieldType == Integer.class || fieldType == int.class) {
            return Integer.parseInt(value);
        }
        
        if (fieldType == Long.class || fieldType == long.class) {
            return Long.parseLong(value);
        }
        
        if (fieldType == Double.class || fieldType == double.class) {
            return Double.parseDouble(value);
        }
        
        if (fieldType == Boolean.class || fieldType == boolean.class) {
            return Boolean.parseBoolean(value);
        }
        
        if (fieldType == List.class) {
            if (this.listSeparatorQuoted == null) {
                return new ArrayList<>();
            }
            
            final Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType) {
                final ParameterizedType paramType = (ParameterizedType) genericType;
                final Type[] typeArgs = paramType.getActualTypeArguments();
                
                if (typeArgs.length > 0 && typeArgs[0] == String.class) {
                    if (value.isEmpty()) {
                        return new ArrayList<String>();
                    }
                    
                    final String[] items = value.split(this.listSeparatorQuoted);
                    final List<String> list = new ArrayList<>();
                    for (final String item : items) {
                        final String trimmed = item.trim();
                        if (!trimmed.isEmpty()) {
                            list.add(trimmed);
                        }
                    }
                    return list;
                }
            }
        }
        
        throw new IllegalArgumentException("Unsupported field type: " + fieldType.getName());
    }
    
    private void addCsvResponseErrorPrompt(final AgentContext context) {
        final var turn = context.getConversation().getConversation().getLast();
        context.getConversation().replaceTurn(new Conversation.Turn(
                turn.type(),
                turn.text() + this.errorPromptSuffix));
    }
}
