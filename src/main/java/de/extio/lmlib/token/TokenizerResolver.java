package de.extio.lmlib.token;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.extio.lmlib.profile.ModelProfile;

public class TokenizerResolver {
	
	private static final List<String> DEFAULT_TOKENIZER_ORDER = List.of(JTokkitTokenizer.NAME, FallbackTokenizer.NAME);
	
	private final Map<String, Tokenizer> tokenizersByName;
	
	public TokenizerResolver(final List<Tokenizer> tokenizers) {
		final var tokenizersByName = new LinkedHashMap<String, Tokenizer>();
		for (final var tokenizer : tokenizers) {
			final var previous = tokenizersByName.put(tokenizer.getName(), tokenizer);
			if (previous != null) {
				throw new IllegalStateException("Duplicate tokenizer name: " + tokenizer.getName());
			}
		}
		this.tokenizersByName = Map.copyOf(tokenizersByName);
	}
	
	public Tokenizer resolve(final ModelProfile modelProfile) {
		if (modelProfile != null && modelProfile.tokenizer() != null && !modelProfile.tokenizer().isBlank()) {
			final var tokenizer = this.tokenizersByName.get(modelProfile.tokenizer());
			if (tokenizer == null) {
				throw new IllegalStateException("No tokenizer configured with name: " + modelProfile.tokenizer());
			}
			return tokenizer;
		}
		for (final var tokenizerName : DEFAULT_TOKENIZER_ORDER) {
			final var tokenizer = this.tokenizersByName.get(tokenizerName);
			if (tokenizer != null) {
				return tokenizer;
			}
		}
		throw new IllegalStateException("No tokenizer available");
	}
	
	public List<Long> tokenize(final String txt, final ModelProfile modelProfile) {
		return this.resolve(modelProfile).tokenize(txt, modelProfile);
	}
	
	public int count(final String txt, final ModelProfile modelProfile) {
		return this.resolve(modelProfile).count(txt, modelProfile);
	}
	
	public String detokenize(final List<Long> tokens, final ModelProfile modelProfile) {
		return this.resolve(modelProfile).detokenize(tokens, modelProfile);
	}
	
}