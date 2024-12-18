package de.extio.lmlib.token;

import java.util.List;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;

import de.extio.lmlib.profile.ModelProfile;

final class JTokkitTokenizer implements Tokenizer {
	
	private static final EncodingRegistry registry;
	
	static {
		registry = Encodings.newDefaultEncodingRegistry();
	}
	
	private final LoadingCache<String, Encoding> encodings = Caffeine
			.newBuilder()
			.build(k -> {
				return registry.getEncoding(EncodingType.fromName(k).orElseThrow(() -> new IllegalArgumentException("Token encoding " + k)));
			});
	
	@Override
	public List<Long> tokenize(final String txt, final ModelProfile modelProfile) {
		return this.encodings
				.get(modelProfile.tokenEncoding())
				.encodeOrdinary(txt)
				.boxed()
				.stream()
				.map(Long::valueOf)
				.toList();
	}
	
	@Override
	public int count(final String txt, final ModelProfile modelProfile) {
		return this.encodings.get(modelProfile.tokenEncoding()).countTokensOrdinary(txt);
	}
	
	@Override
	public String detokenize(final List<Long> tokens, final ModelProfile modelProfile) {
		final var ial = new IntArrayList(tokens.size());
		tokens.forEach(t -> ial.add(t.intValue()));
		return this.encodings
				.get(modelProfile.tokenEncoding())
				.decode(ial);
	}
}
