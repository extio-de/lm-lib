package de.extio.lmlib.client.cached;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;
import de.extio.lmlib.profile.ModelProfileService;

public class CachedClient implements Client {
	
	private final CachedClientRepository cacheRepository;
	
	private final ModelProfileService modelProfileService;
	
	private final Client client;
	
	public CachedClient(final CachedClientRepository cacheRepository, final ModelProfileService modelProfileService, final Client client) {
		this.cacheRepository = cacheRepository;
		this.modelProfileService = modelProfileService;
		this.client = client;
	}
	
	@Override
	public Completion completion(final ModelCategory modelCategory, final String system, final String question, final String fullText) {
		return this.getCachedResponse(modelCategory, () -> this.client.completion(modelCategory, system, question, fullText), system, question, fullText);
	}
	
	@Override
	public Completion conversation(final ModelCategory modelCategory, final Conversation conversation) {
		return this.getCachedResponse(modelCategory, () -> this.client.conversation(modelCategory, conversation), conversation.getConversation().toString());
	}
	
	@Override
	public ModelProvider getModelProvider() {
		return this.client.getModelProvider();
	}
	
	private Completion getCachedResponse(final ModelCategory modelCategory, final Supplier<Completion> supplier, final String... keys) {
		final var key = this.createCacheKey(modelCategory, keys);
		
		final var cachedCompletion = this.cacheRepository.get(key);
		if (cachedCompletion != null) {
			return new Completion(cachedCompletion.response(), 0, Duration.ZERO, cachedCompletion.inTokens(), cachedCompletion.outTokens(), BigDecimal.ZERO, true);
		}
		
		final var completion = supplier.get();
		if (completion != null) {
			this.cacheRepository.put(key, new CachedCompletion(completion.response(), completion.inTokens(), completion.outTokens(), OffsetDateTime.now()));
		}
		return completion;
	}
	
	private String createCacheKey(ModelCategory modelCategory, final String... keys) {
		if (modelCategory == null) {
			modelCategory = ModelCategory.MEDIUM;
		}
		final var modelProfile = this.modelProfileService.getModelProfile(modelCategory.getModelProfile());
		if (modelProfile == null) {
			throw new IllegalArgumentException("ModelProfile " + modelCategory.getModelProfile());
		}
		
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
			digest.update(Objects.requireNonNullElse(modelProfile.modelName(), modelCategory.name()).getBytes());
			digest.update(String.valueOf(modelProfile.modelProvider()).getBytes());
			digest.update(String.valueOf(modelProfile.maxTokens()).getBytes());
			digest.update(String.valueOf(modelProfile.maxContextLength()).getBytes());
			digest.update(String.valueOf(modelProfile.maxContinuations()).getBytes());
			digest.update(String.valueOf(modelProfile.temperature()).getBytes());
			digest.update(String.valueOf(modelProfile.topP()).getBytes());
			for (final var k : keys) {
				if (k != null && !k.isEmpty()) {
					digest.update(k.getBytes());
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (final NoSuchAlgorithmException e) {
			throw new RuntimeException("Error calculating hash", e);
		}
	}
	
}
