package de.extio.lmlib.agent.responsehandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

final class JsonSupport {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonSupport.class);
	
	private static final int MAX_EXTRACTION_ATTEMPTS = 16;
	
	static final ObjectMapper MAPPER = createPermissiveMapper();
	
	private JsonSupport() {
	}
	
	private static ObjectMapper createPermissiveMapper() {
		return JsonMapper.builder()
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
	
	static JsonNode parseStructure(final String response) {
		final int length = response.length();
		int attempts = 0;
		for (int start = 0; start < length && attempts < MAX_EXTRACTION_ATTEMPTS; start++) {
			final char open = response.charAt(start);
			if (open != '{' && open != '[') {
				continue;
			}
			attempts++;
			final String candidate = extractSpan(response, start, open, open == '{' ? '}' : ']');
			if (candidate == null) {
				continue;
			}
			JsonNode node;
			try {
				node = MAPPER.readTree(candidate);
			}
			catch (final Exception first) {
				LOGGER.debug("Initial JSON parse failed, attempting to sanitize: {}", first.getMessage());
				try {
					node = MAPPER.readTree(sanitizeJson(candidate));
					LOGGER.debug("Successfully parsed after sanitization");
				}
				catch (final Exception second) {
					continue;
				}
			}
			if (node != null && (node.isObject() || node.isArray())) {
				return node;
			}
		}
		return null;
	}
	
	private static String extractSpan(final String response, final int start, final char open, final char close) {
		int depth = 0;
		boolean inString = false;
		boolean escaped = false;
		for (int i = start; i < response.length(); i++) {
			final char c = response.charAt(i);
			if (escaped) {
				escaped = false;
				continue;
			}
			if (inString) {
				if (c == '\\') {
					escaped = true;
				}
				else if (c == '"') {
					inString = false;
				}
				continue;
			}
			if (c == '"') {
				inString = true;
			}
			else if (c == open) {
				depth++;
			}
			else if (c == close) {
				depth--;
				if (depth == 0) {
					return response.substring(start, i + 1);
				}
			}
		}
		final int end = response.lastIndexOf(close);
		return end > start ? response.substring(start, end + 1) : null;
	}
	
	static String sanitizeJson(final String json) {
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
