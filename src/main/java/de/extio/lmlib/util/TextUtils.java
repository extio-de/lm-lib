package de.extio.lmlib.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

public class TextUtils {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TextUtils.class);
	
	private static final List<String> PARAGRAPH_DELIMITERS = List.of("\n\n", "\n", ".", "!", "?");
	
	public static String normalizeText(final String text) {
		String result = text;
		
		// Remove excessive newlines, spaces and tabs
		int cnt = 0;
		do {
			cnt = result.length();
			result = result.replace("\r", "\n");
			result = result.replace("\n\n\n", "\n\n");
			result = result.replace("\t", "");
			result = result.replace("  ", " ");
			result = result.replace(" .", ".");
			result = result.replace(".  ", ". ");
			result = result.replace("\n.", ".");
			result = result.replace(" !", "!");
			result = result.replace("!  ", "! ");
			result = result.replace("\n!", "!");
			result = result.replace(" ?", "?");
			result = result.replace("?  ", "? ");
			result = result.replace("\n?", "?");
			result = result.replace(" \n", "\n");
		} while (result.length() != cnt);
		
		// Remove newlines mid of the sentence
		final StringBuilder sb = new StringBuilder(result);
		for (int i = 1; i < sb.length(); i++) {
			final char c = sb.charAt(i);
			if (c == '\n' && sb.charAt(i - 1) != '.' && sb.charAt(i - 1) != '?' && sb.charAt(i - 1) != '!' && sb.charAt(i - 1) != '\n') {
				sb.setCharAt(i, ' ');
			}
		}
		result = sb.toString();
		
		LOGGER.info("Normalizer stats: Before: {} After {}", text.length(), result.length());
		
		return result;
	}
	
	public static String normalizeModelResponse(final String response, final boolean removePreamble) {
		if (response == null) {
			return null;
		}
		
		String result = response.replace("\r", "");
		
		if (removePreamble) {
			final int colon = result.indexOf(':');
			final int dot = result.indexOf('.');
			int firstLineEnd = result.indexOf('\n');
			final boolean colonInFirstLine = firstLineEnd == -1 || colon != -1 && colon < firstLineEnd;
			final boolean dotInFirstLine = firstLineEnd == -1 || dot != -1 && dot < firstLineEnd;
			
			if (colon > -1 && colon < result.length() - 2 && (colon < 50 || result.charAt(colon + 1) == '\n') && colonInFirstLine && !dotInFirstLine) {
				result = result.substring(colon + 1);
			}
			firstLineEnd = result.indexOf('\n');
			if (firstLineEnd != -1) {
				String firstLine = result.substring(0, firstLineEnd);
				firstLine = stripChars(firstLine.trim(), "*#´`'“”„‟«»\"\n");
				result = firstLine + result.substring(firstLineEnd);
			}
			else {
				result = stripChars(result, "*#´`'“”„‟«»\"\n");
			}
		}
		
		result = result.trim();
		if (!removePreamble) {
			result = stripChars(result, "´`'“”„‟«»\"\n");
		}
		result = result.trim();
		
		return result;
	}
	
	private static String stripChars(final String str, final String chars) {
		if (str == null || str.isEmpty())
			return str;
		int start = 0, end = str.length();
		
		while (start < end && chars.indexOf(str.charAt(start)) >= 0)
			start++;
		while (end > start && chars.indexOf(str.charAt(end - 1)) >= 0)
			end--;
		
		return str.substring(start, end);
	}
	
	public static List<String> splitParagraphs(final String text, final int chunks_norm, final int chunks_var, final boolean slidingWindows) {
		final int CHUNKS_MIN = chunks_norm - chunks_var;
		final int CHUNKS_MAX = chunks_norm + chunks_var;
		
		if (text.length() <= CHUNKS_MAX) {
			return Collections.singletonList(text);
		}
		
		final List<String> splits = new ArrayList<>();
		
		int pos = 0;
		do {
			int next = -1;
			int delLength = 1;
			if (text.length() - pos > CHUNKS_MAX) {
				for (final String delimiter : PARAGRAPH_DELIMITERS) {
					final int oForw = text.substring(Math.min(pos + chunks_norm, text.length()), Math.min(pos + CHUNKS_MAX, text.length())).indexOf(delimiter);
					final int oBack = text.substring(Math.min(pos + CHUNKS_MIN, text.length()), Math.min(pos + chunks_norm, text.length())).lastIndexOf(delimiter);
					int o = -1;
					if (oForw > -1 && oBack == -1) {
						o = pos + chunks_norm + oForw;
					}
					else if (oForw == -1 && oBack > -1) {
						o = pos + CHUNKS_MIN + oBack;
					}
					else if (oForw > -1 && oBack > -1) {
						o = oForw <= (chunks_var - oBack) ? pos + chunks_norm + oForw : pos + CHUNKS_MIN + oBack;
					}
					
					if (o > -1) {
						next = o;
						delLength = delimiter.length();
						break;
					}
				}
			}
			if (next == -1) {
				next = Math.min(pos + CHUNKS_MAX, text.length());
			}
			
			if (slidingWindows && pos > chunks_var) {
				for (final String delimiter : PARAGRAPH_DELIMITERS) {
					final int o = text.indexOf(delimiter, pos - chunks_var);
					if (o > -1 && o < pos - 10) {
						pos = o + delimiter.length();
						break;
					}
				}
			}
			String split = text.substring(pos, next).trim();
			if (!split.endsWith(".") && !split.endsWith("!") && !split.endsWith("?")) {
				split = split + ".";
			}
			splits.add(split);
			
			pos = next + delLength;
		} while (pos < text.length());
		
		LOGGER.info("Created {} splits", splits.size());
		
		return splits;
	}
	
	public static String capitalizeWords(final String input) {
		final String[] words = input.split(" ");
		final StringBuilder capitalizedWords = new StringBuilder();
		
		for (final String word : words) {
			if (!word.isEmpty()) {
				capitalizedWords.append(Character.toUpperCase(word.charAt(0)))
						.append(word.substring(1).toLowerCase())
						.append(" ");
			}
		}
		return capitalizedWords.toString().trim();
	}
	
	private static final LoadingCache<String, Pattern> wholeWordRegexCache = Caffeine.newBuilder()
			.maximumSize(1000)
			.build(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
	
	public static boolean matchWholeWordCaseInsensitive(final String text, final String word) {
		final String regex = "\\b" + Pattern.quote(word) + "\\b";
		final Pattern pattern = wholeWordRegexCache.get(regex);
		return pattern.matcher(text).find();
	}
}
