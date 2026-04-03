package de.extio.lmlib.client.cached;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import tools.jackson.databind.ObjectMapper;

import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.CompletionStatistics;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.ToolCallData;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;
import de.extio.lmlib.profile.ModelProfileService;

public class CachedClient implements Client {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	
	private final CachedClientRepository cacheRepository;
	
	private final ModelProfileService modelProfileService;
	
	private final Client client;
	
	public CachedClient(final CachedClientRepository cacheRepository, final ModelProfileService modelProfileService, final Client client) {
		this.cacheRepository = cacheRepository;
		this.modelProfileService = modelProfileService;
		this.client = client;
	}
	
	@Override
	public Completion conversation(final ModelCategory modelCategory, final Conversation conversation, final ToolCallData toolCallData, final boolean skipCache) {
		return this.getCachedResponse(modelCategory, null, skipCache, () -> this.client.conversation(modelCategory, conversation, toolCallData, skipCache), conversation.getConversation().toString(), this.serializeToolCallData(toolCallData));
	}
	
	@Override
	public Completion conversation(final ModelProfile modelProfile, final Conversation conversation, final ToolCallData toolCallData, final boolean skipCache) {
		return this.getCachedResponse(null, modelProfile, skipCache, () -> this.client.conversation(modelProfile, conversation, toolCallData, skipCache), conversation.getConversation().toString(), this.serializeToolCallData(toolCallData));
	}

	@Override
	public Completion streamConversation(final ModelCategory modelCategory, final Conversation conversation, final Consumer<Chunk> chunkConsumer, final ToolCallData toolCallData, final boolean skipCache) {
		final var response = this.getCachedResponse(modelCategory, null, skipCache, () -> this.client.streamConversation(modelCategory, conversation, chunkConsumer, toolCallData, skipCache), conversation.getConversation().toString(), this.serializeToolCallData(toolCallData));
		if (response.statistics().cached()) {
			chunkConsumer.accept(new Chunk(response.response(), response.reasoning()));
		}
		return response;
	}
	
	@Override
	public Completion streamConversation(final ModelProfile modelProfile, final Conversation conversation, final Consumer<Chunk> chunkConsumer, final ToolCallData toolCallData, final boolean skipCache) {
		final var response = this.getCachedResponse(null, modelProfile, skipCache, () -> this.client.streamConversation(modelProfile, conversation, chunkConsumer, toolCallData, skipCache), conversation.getConversation().toString(), this.serializeToolCallData(toolCallData));
		if (response.statistics().cached()) {
			chunkConsumer.accept(new Chunk(response.response(), response.reasoning()));
		}
		return response;
	}

	@Override
	public List<String> getModelNames(final ModelProfile modelProfile, final boolean forceReload) {
		return this.client.getModelNames(modelProfile, forceReload);
	}
	
	@Override
	public ModelProvider getModelProvider() {
		return this.client.getModelProvider();
	}

	@Override
	public boolean supportsToolCalling() {
		return this.client.supportsToolCalling();
	}
	
	private Completion getCachedResponse(final ModelCategory modelCategory, final ModelProfile modelProfile, final boolean skipCache, final Supplier<Completion> supplier, final String... keys) {
		final var key = this.createCacheKey(modelCategory, modelProfile, keys);
		
		final var cachedCompletion = skipCache ? null : this.cacheRepository.get(key);
		if (cachedCompletion != null) {
			return new Completion(cachedCompletion.response(), cachedCompletion.reasoning(), cachedCompletion.finishReason(), new CompletionStatistics(0, Duration.ZERO, cachedCompletion.inTokens(), cachedCompletion.cachedInTokens(), cachedCompletion.outTokens(), cachedCompletion.reasoningOutTokens(), BigDecimal.ZERO, true), cachedCompletion.toolCalls());
		}
		
		final var completion = supplier.get();
		if (completion != null) {
			this.cacheRepository.put(key, new CachedCompletion(completion.response(), completion.reasoning(), completion.finishReason(), completion.toolCalls(), completion.statistics().inTokens(), completion.statistics().cachedInTokens(), completion.statistics().outTokens(), completion.statistics().reasoningOutTokens(), OffsetDateTime.now()));
		}
		return completion;
	}

	private String serializeToolCallData(final ToolCallData toolCallData) {
		if (toolCallData == null) {
			return null;
		}
		try {
			return OBJECT_MAPPER.writeValueAsString(toolCallData);
		}
		catch (final Exception e) {
			return toolCallData.toString();
		}
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
			digest.update(this.resolvePrimaryModelName(modelProfile).getBytes());
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

	private String resolvePrimaryModelName(final ModelProfile modelProfile) {
		if (modelProfile.modelName() != null && !modelProfile.modelName().isBlank()) {
			return modelProfile.modelName();
		}
		return this.client.getModelNames(modelProfile, false).stream().findFirst().orElse("");
	}
	
}
