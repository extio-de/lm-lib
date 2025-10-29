package de.extio.lmlib.client.cached;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.CompletionFinishReason;
import de.extio.lmlib.client.CompletionStatistics;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile;
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
	public Completion completion(final ModelCategory modelCategory, final String system, final String text) {
		return this.getCachedResponse(modelCategory, null, () -> this.client.completion(modelCategory, system, text), system, text);
	}
	
	@Override
	public Completion conversation(final ModelCategory modelCategory, final Conversation conversation) {
		return this.getCachedResponse(modelCategory, null, () -> this.client.conversation(modelCategory, conversation), conversation.getConversation().toString());
	}
	
	@Override
	public Completion conversation(final ModelProfile modelProfile, final Conversation conversation) {
		return this.getCachedResponse(null, modelProfile, () -> this.client.conversation(modelProfile, conversation), conversation.getConversation().toString());
	}

	@Override
	public Completion streamConversation(final ModelCategory modelCategory, final Conversation conversation, final Consumer<Chunk> chunkConsumer) {
		final var response = this.getCachedResponse(modelCategory, null, () -> this.client.streamConversation(modelCategory, conversation, chunkConsumer), conversation.getConversation().toString());
		if (response.statistics().cached()) {
			chunkConsumer.accept(new Chunk(response.response(), response.reasoning()));
		}
		return response;
	}
	
	@Override
	public Completion streamConversation(final ModelProfile modelProfile, final Conversation conversation, final Consumer<Chunk> chunkConsumer) {
		final var response = this.getCachedResponse(null, modelProfile, () -> this.client.streamConversation(modelProfile, conversation, chunkConsumer), conversation.getConversation().toString());
		if (response.statistics().cached()) {
			chunkConsumer.accept(new Chunk(response.response(), response.reasoning()));
		}
		return response;
	}
	
	@Override
	public ModelProvider getModelProvider() {
		return this.client.getModelProvider();
	}
	
	private Completion getCachedResponse(final ModelCategory modelCategory, final ModelProfile modelProfile, final Supplier<Completion> supplier, final String... keys) {
		final var key = this.createCacheKey(modelCategory, modelProfile, keys);
		
		final var cachedCompletion = this.cacheRepository.get(key);
		if (cachedCompletion != null) {
			return new Completion(cachedCompletion.response(), cachedCompletion.reasoning(), CompletionFinishReason.DONE, new CompletionStatistics(0, Duration.ZERO, cachedCompletion.inTokens(), cachedCompletion.outTokens(), BigDecimal.ZERO, true));
		}
		
		final var completion = supplier.get();
		if (completion != null) {
			this.cacheRepository.put(key, new CachedCompletion(completion.response(), completion.reasoning(), completion.finishReason(), completion.statistics().inTokens(), completion.statistics().outTokens(), OffsetDateTime.now()));
		}
		return completion;
	}
	
	private String createCacheKey(ModelCategory modelCategory, ModelProfile modelProfile, final String... keys) {
		if (modelProfile == null) {
			if (modelCategory == null) {
				modelCategory = ModelCategory.MEDIUM;
			}
			modelProfile = this.modelProfileService.getModelProfile(modelCategory.getModelProfile(), modelCategory);
			if (modelProfile == null) {
				throw new IllegalArgumentException("ModelProfile " + modelCategory.getModelProfile());
			}
		}
		
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
			digest.update(modelProfile.modelName().getBytes());
			digest.update(modelCategory != null ? modelCategory.shortName().getBytes() : modelProfile.category().getBytes());
			digest.update(String.valueOf(modelProfile.modelProvider()).getBytes());
			digest.update(String.valueOf(modelProfile.maxTokens()).getBytes());
			digest.update(String.valueOf(modelProfile.maxContextLength()).getBytes());
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
