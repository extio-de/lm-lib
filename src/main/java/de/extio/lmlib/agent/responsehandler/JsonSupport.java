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
	
	/**
	 * Extracts the first JSON object or array of the response that can be parsed.
	 * Structure boundaries respect double and single quoted strings as well as line and
	 * block comments, so delimiters inside them do not corrupt the result. If a candidate
	 * cannot be parsed, even after sanitization, the whole candidate is skipped: a broken
	 * structure is never replaced by one of its inner structures, so incomplete responses
	 * surface as errors instead of silently yielding partial data.
	 */
	static JsonNode parseStructure(final String response) {
		if (response == null) {
			return null;
		}
		final int length = response.length();
		final int lastCurly = response.lastIndexOf('}');
		final int lastSquare = response.lastIndexOf(']');
		int start = 0;
		while (start < length) {
			final char open = response.charAt(start);
			if (open != '{' && open != '[') {
				start++;
				continue;
			}
			int end = findBalancedEnd(response, start, open, open == '{' ? '}' : ']');
			if (end < 0) {
				// unterminated structure, fall back to the last matching delimiter so
				// that the sanitizer can still repair simple defects such as a missing closing quote
				end = open == '{' ? lastCurly : lastSquare;
				if (end <= start) {
					start++;
					continue;
				}
			}
			final JsonNode node = readTree(response.substring(start, end + 1));
			if (node != null && (node.isObject() || node.isArray())) {
				return node;
			}
			start = end + 1;
		}
		return null;
	}
	
	private static JsonNode readTree(final String candidate) {
		try {
			return MAPPER.readTree(candidate);
		}
		catch (final Exception first) {
			LOGGER.debug("Initial JSON parse failed, attempting to sanitize: {}", first.getMessage());
		}
		try {
			final JsonNode node = MAPPER.readTree(sanitizeJson(candidate));
			LOGGER.debug("Successfully parsed after sanitization");
			return node;
		}
		catch (final Exception second) {
			return null;
		}
	}
	
	private static int findBalancedEnd(final String response, final int start, final char open, final char close) {
		int depth = 0;
		int i = start;
		while (i < response.length()) {
			final char c = response.charAt(i);
			if (c == '"' || c == '\'') {
				i = skipString(response, i, c);
				continue;
			}
			if (c == '/') {
				final int afterComment = skipComment(response, i);
				if (afterComment > i) {
					i = afterComment;
					continue;
				}
			}
			if (c == open) {
				depth++;
			}
			else if (c == close && --depth == 0) {
				return i;
			}
			i++;
		}
		return -1;
	}
	
	/**
	 * Returns the index after the closing quote of the string starting at {@code start},
	 * or the end of the response when the string is never closed.
	 */
	private static int skipString(final String response, final int start, final char quote) {
		int i = start + 1;
		while (i < response.length()) {
			final char c = response.charAt(i);
			if (c == '\\') {
				i += 2;
				continue;
			}
			if (c == quote) {
				return i + 1;
			}
			i++;
		}
		return response.length();
	}
	
	/**
	 * Returns the index after a line or block comment starting at {@code start},
	 * or {@code start} when there is no comment at this position.
	 */
	private static int skipComment(final String response, final int start) {
		if (start + 1 >= response.length()) {
			return start;
		}
		final char next = response.charAt(start + 1);
		if (next == '/') {
			final int newline = response.indexOf('\n', start + 2);
			return newline < 0 ? response.length() : newline;
		}
		if (next == '*') {
			final int end = response.indexOf("*/", start + 2);
			return end < 0 ? response.length() : end + 2;
		}
		return start;
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
