package de.extio.lmlib.token;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.extio.lmlib.profile.ModelProfile;

final class FallbackTokenizer implements Tokenizer {
	
	static final String NAME = "fallback";
	
	@Override
	public String getName() {
		return NAME;
	}
	
	@Override
	public List<Long> tokenize(final String txt, final ModelProfile modelProfile) {
		if (txt == null || txt.isEmpty()) {
			return List.of();
		}
		final var tokens = new ArrayList<Long>();
		var index = 0;
		while (index < txt.length()) {
			final var codePoint = txt.codePointAt(index);
			final var charCount = Character.charCount(codePoint);
			if (Character.isWhitespace(codePoint)) {
				final var end = this.consumeWhitespace(txt, index);
				tokens.addAll(this.encodeChunked(txt.substring(index, end), 6));
				index = end;
				continue;
			}
			if (Character.isLetterOrDigit(codePoint)) {
				final var end = this.consumeAlphaNumeric(txt, index);
				tokens.addAll(this.encodeWordLike(txt.substring(index, end)));
				index = end;
				continue;
			}
			tokens.add(this.encodeToken(txt.substring(index, index + charCount)));
			index += charCount;
		}
		return List.copyOf(tokens);
	}
	
	@Override
	public int count(final String txt, final ModelProfile modelProfile) {
		return this.tokenize(txt, modelProfile).size();
	}
	
	@Override
	public String detokenize(final List<Long> tokens, final ModelProfile modelProfile) {
		final var builder = new StringBuilder();
		for (final var token : tokens) {
			builder.append(this.decodeToken(token));
		}
		return builder.toString();
	}
	
	private int consumeWhitespace(final String txt, final int start) {
		var index = start;
		while (index < txt.length()) {
			final var codePoint = txt.codePointAt(index);
			if (!Character.isWhitespace(codePoint)) {
				break;
			}
			index += Character.charCount(codePoint);
		}
		return index;
	}
	
	private int consumeAlphaNumeric(final String txt, final int start) {
		var index = start;
		while (index < txt.length()) {
			final var codePoint = txt.codePointAt(index);
			if (!Character.isLetterOrDigit(codePoint)) {
				break;
			}
			index += Character.charCount(codePoint);
		}
		return index;
	}
	
	private List<Long> encodeWordLike(final String text) {
		if (text.isEmpty()) {
			return List.of();
		}
		if (this.isAsciiDigits(text)) {
			return this.encodeChunked(text, 3);
		}
		if (this.isAsciiLetters(text)) {
			return this.encodeChunked(text, 4);
		}
		return this.encodeChunkedByUtf8Bytes(text, 6);
	}
	
	private boolean isAsciiDigits(final String text) {
		return text.chars().allMatch(ch -> ch >= '0' && ch <= '9');
	}
	
	private boolean isAsciiLetters(final String text) {
		return text.chars().allMatch(ch -> ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z');
	}
	
	private List<Long> encodeChunked(final String text, final int maxCharsPerToken) {
		final var tokens = new ArrayList<Long>();
		for (var index = 0; index < text.length();) {
			var end = index;
			var charCount = 0;
			while (end < text.length() && charCount < maxCharsPerToken) {
				final var codePoint = text.codePointAt(end);
				end += Character.charCount(codePoint);
				charCount++;
			}
			tokens.add(this.encodeToken(text.substring(index, end)));
			index = end;
		}
		return tokens;
	}
	
	private List<Long> encodeChunkedByUtf8Bytes(final String text, final int maxBytesPerToken) {
		final var tokens = new ArrayList<Long>();
		for (var index = 0; index < text.length();) {
			var end = index;
			var bytes = 0;
			while (end < text.length()) {
				final var codePoint = text.codePointAt(end);
				final var candidateEnd = end + Character.charCount(codePoint);
				final var candidateBytes = text.substring(end, candidateEnd).getBytes(StandardCharsets.UTF_8).length;
				if (bytes > 0 && bytes + candidateBytes > maxBytesPerToken) {
					break;
				}
				end = candidateEnd;
				bytes += candidateBytes;
				if (bytes >= maxBytesPerToken) {
					break;
				}
			}
			tokens.add(this.encodeToken(text.substring(index, end)));
			index = end;
		}
		return tokens;
	}
	
	private long encodeToken(final String tokenText) {
		final var bytes = tokenText.getBytes(StandardCharsets.UTF_8);
		if (bytes.length == 0 || bytes.length > 7) {
			throw new IllegalArgumentException("Fallback tokenizer token length must be between 1 and 7 bytes");
		}
		long value = bytes.length;
		for (var i = 0; i < bytes.length; i++) {
			value |= (bytes[i] & 0xffL) << ((i + 1) * 8);
		}
		return value;
	}
	
	private String decodeToken(final long token) {
		final var length = (int) (token & 0xffL);
		if (length <= 0 || length > 7) {
			throw new IllegalArgumentException("Invalid fallback tokenizer token length: " + length);
		}
		final var bytes = new byte[length];
		for (var i = 0; i < length; i++) {
			bytes[i] = (byte) ((token >> ((i + 1) * 8)) & 0xffL);
		}
		return new String(bytes, StandardCharsets.UTF_8);
	}
	
}
