package de.extio.lmlib.token;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.extio.lmlib.profile.ModelProfile;

final class FallbackTokenizer implements Tokenizer {
	
	static final String NAME = "fallback";
	
	private static final int WORD_CLASS = 0;
	private static final int WHITESPACE_CLASS = 1;
	private static final int PUNCTUATION_CLASS = 2;
	private static final int NON_ASCII_CLASS = 3;
	
	private static final int WORD_BYTES_PER_TOKEN = 7;
	private static final int DIGIT_BYTES_PER_TOKEN = 3;
	private static final int WHITESPACE_CHARS_PER_TOKEN = 4;
	private static final int PUNCTUATION_CHARS_PER_TOKEN = 3;
	
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
			final var cls = this.classOf(txt.codePointAt(index));
			if (cls == WHITESPACE_CLASS) {
				final var whitespaceEnd = this.consumeRun(txt, index, WHITESPACE_CLASS);
				final var next = whitespaceEnd < txt.length() ? this.classOf(txt.codePointAt(whitespaceEnd)) : NON_ASCII_CLASS;
				if (next == WORD_CLASS || next == PUNCTUATION_CLASS) {
					final var wordEnd = this.consumeRun(txt, whitespaceEnd, next);
					tokens.addAll(this.encodeChunked(txt.substring(index, wordEnd), this.bytesPerToken(txt.substring(whitespaceEnd, wordEnd))));
					index = wordEnd;
				} else {
					tokens.addAll(this.encodeChunkedChars(txt.substring(index, whitespaceEnd), WHITESPACE_CHARS_PER_TOKEN));
					index = whitespaceEnd;
				}
				continue;
			}
			if (cls == WORD_CLASS) {
				final var end = this.consumeRun(txt, index, WORD_CLASS);
				tokens.addAll(this.encodeChunked(txt.substring(index, end), this.bytesPerToken(txt.substring(index, end))));
				index = end;
				continue;
			}
			if (cls == PUNCTUATION_CLASS) {
				final var end = this.consumeRun(txt, index, PUNCTUATION_CLASS);
				tokens.addAll(this.encodeChunkedChars(txt.substring(index, end), PUNCTUATION_CHARS_PER_TOKEN));
				index = end;
				continue;
			}
			final var charCount = Character.charCount(txt.codePointAt(index));
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
	
	private int classOf(final int codePoint) {
		if (codePoint >= 0x80) {
			return Character.isLetterOrDigit(codePoint) ? WORD_CLASS : NON_ASCII_CLASS;
		}
		if ((codePoint >= 'a' && codePoint <= 'z') || (codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= '0' && codePoint <= '9')) {
			return WORD_CLASS;
		}
		if (codePoint == ' ' || codePoint == '\t' || codePoint == '\n' || codePoint == '\r' || codePoint == '\f') {
			return WHITESPACE_CLASS;
		}
		return PUNCTUATION_CLASS;
	}
	
	private int consumeRun(final String txt, final int start, final int cls) {
		var index = start;
		while (index < txt.length() && this.classOf(txt.codePointAt(index)) == cls) {
			index += Character.charCount(txt.codePointAt(index));
		}
		return index;
	}
	
	private int bytesPerToken(final String text) {
		return this.isAsciiDigits(text) ? DIGIT_BYTES_PER_TOKEN : WORD_BYTES_PER_TOKEN;
	}
	
	private boolean isAsciiDigits(final String text) {
		return text.codePoints().allMatch(cp -> cp >= '0' && cp <= '9');
	}
	
	private List<Long> encodeChunked(final String text, final int maxBytesPerToken) {
		final var tokens = new ArrayList<Long>();
		for (var index = 0; index < text.length();) {
			var end = index;
			var bytes = 0;
			while (end < text.length()) {
				final var codePoint = text.codePointAt(end);
				final var candidateBytes = this.utf8Length(codePoint);
				if (bytes + candidateBytes > maxBytesPerToken) {
					break;
				}
				end += Character.charCount(codePoint);
				bytes += candidateBytes;
			}
			if (end == index) {
				end += Character.charCount(text.codePointAt(index));
			}
			tokens.add(this.encodeToken(text.substring(index, end)));
			index = end;
		}
		return tokens;
	}
	
	private List<Long> encodeChunkedChars(final String text, final int maxCharsPerToken) {
		final var tokens = new ArrayList<Long>();
		for (var index = 0; index < text.length();) {
			final var end = Math.min(index + maxCharsPerToken, text.length());
			tokens.add(this.encodeToken(text.substring(index, end)));
			index = end;
		}
		return tokens;
	}
	
	private int utf8Length(final int codePoint) {
		if (codePoint < 0x80) {
			return 1;
		}
		if (codePoint < 0x800) {
			return 2;
		}
		if (codePoint < 0x10000) {
			return 3;
		}
		return 4;
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
