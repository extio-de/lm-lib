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
	
	static final String NAME = "jTokkit";
	
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
	public String getName() {
		return NAME;
	}
	
	@Override
	public List<Long> tokenize(final String txt, final ModelProfile modelProfile) {
		return this.encodings
				.get(this.getTokenEncoding(modelProfile))
				.encodeOrdinary(txt)
				.boxed()
				.stream()
				.map(Long::valueOf)
				.toList();
	}
	
	@Override
	public int count(final String txt, final ModelProfile modelProfile) {
		return this.encodings.get(this.getTokenEncoding(modelProfile)).countTokensOrdinary(txt);
	}
	
	@Override
	public String detokenize(final List<Long> tokens, final ModelProfile modelProfile) {
		final var ial = new IntArrayList(tokens.size());
		tokens.forEach(t -> ial.add(t.intValue()));
		return this.encodings
				.get(this.getTokenEncoding(modelProfile))
				.decode(ial);
	}

	private String getTokenEncoding(final ModelProfile modelProfile) {
		if (modelProfile.tokenEncoding() == null || modelProfile.tokenEncoding().isBlank() || "none".equalsIgnoreCase(modelProfile.tokenEncoding())) {
			return "cl100k_base"; // default encoding, used by most models. Will be used if the profile doesn't specify an encoding or specifies an invalid one.
		}
		return modelProfile.tokenEncoding();
	}
}
