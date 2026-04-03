package de.extio.lmlib.token;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;

public class TokenizerResolverTest {
	
	@Test
	void resolvesTokenizerFromProfileName() {
		final var fallbackTokenizer = new FallbackTokenizer();
		final var customTokenizer = new NamedTokenizer("custom");
		final var resolver = new TokenizerResolver(List.of(fallbackTokenizer, customTokenizer));
		
		assertSame(customTokenizer, resolver.resolve(this.createProfile("custom")));
	}
	
	@Test
	void prefersJTokkitAsDefaultWhenAvailable() {
		final var fallbackTokenizer = new FallbackTokenizer();
		final var jTokkitTokenizer = new NamedTokenizer(JTokkitTokenizer.NAME);
		final var resolver = new TokenizerResolver(List.of(fallbackTokenizer, jTokkitTokenizer));
		
		assertSame(jTokkitTokenizer, resolver.resolve(this.createProfile(null)));
	}
	
	@Test
	void fallsBackWhenNoPreferredTokenizerExists() {
		final var fallbackTokenizer = new FallbackTokenizer();
		final var resolver = new TokenizerResolver(List.of(fallbackTokenizer));
		
		assertSame(fallbackTokenizer, resolver.resolve(this.createProfile(null)));
	}
	
	private ModelProfile createProfile(final String tokenizer) {
		return new ModelProfile(
				"",
				tokenizer,
				"cl100k_base",
				1024,
				4096,
				0.4,
				1.0,
				ModelProvider.OAI_CHAT_COMPLETION,
				"test-model",
				"http://localhost:5001",
				"",
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				null,
				null,
				"TEST");
	}
	
	private static final class NamedTokenizer implements Tokenizer {
		
		private final String name;
		
		private NamedTokenizer(final String name) {
			this.name = name;
		}
		
		@Override
		public String getName() {
			return this.name;
		}
		
		@Override
		public List<Long> tokenize(final String txt, final ModelProfile modelProfile) {
			return List.of();
		}
		
		@Override
		public int count(final String txt, final ModelProfile modelProfile) {
			return 0;
		}
		
		@Override
		public String detokenize(final List<Long> tokens, final ModelProfile modelProfile) {
			return "";
		}
	}
	
}