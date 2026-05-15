package de.extio.lmlib.client.openrouter;

import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.client.ToolCallData;
import de.extio.lmlib.client.oai.OpenAiProviderDialect;
import de.extio.lmlib.client.oai.completion.chat.ChatCompletionClient;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;

public class OpenRouterClient extends ChatCompletionClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(OpenRouterClient.class);

	@Autowired
	private OpenRouterModelDiscovery openRouterModelDiscovery;

	@Override
	public Completion conversation(final ModelCategory modelCategory, final Conversation conversation, final ToolCallData toolCallData, final boolean skipCache) {
		return this.streamConversation(modelCategory, conversation, null, toolCallData, skipCache);
	}

	@Override
	public Completion conversation(final ModelProfile modelProfile, final Conversation conversation, final ToolCallData toolCallData, final boolean skipCache) {
		return this.streamConversation(modelProfile, conversation, null, toolCallData, skipCache);
	}

	@Override
	public Completion streamConversation(final ModelCategory modelCategory, final Conversation conversation, final Consumer<Chunk> chunkConsumer, final ToolCallData toolCallData, final boolean skipCache) {
		final var modelProfile = this.modelProfileService.getModelProfile(modelCategory.getModelProfile(), modelCategory);
		return this.streamConversation(modelProfile, conversation, chunkConsumer, toolCallData, skipCache);
	}

	@Override
	public Completion streamConversation(final ModelProfile modelProfile, final Conversation conversation, final Consumer<Chunk> chunkConsumer, final ToolCallData toolCallData, final boolean skipCache) {
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.OPENROUTER) {
			throw new IllegalArgumentException("Invalid ModelProfile");
		}
		return this.requestCompletion(conversation, modelProfile, chunkConsumer, toolCallData);
	}

	@Override
	public List<String> getModelNames(final ModelProfile modelProfile, final boolean forceReload) {
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.OPENROUTER) {
			return List.of();
		}
		return this.openRouterModelDiscovery.listModels(modelProfile, new OpenRouterModelQuery(true, null, List.of(), List.of()), forceReload).stream()
				.map(OpenRouterModelsResponse.OpenRouterModel::getId)
				.filter(modelName -> modelName != null && !modelName.isBlank())
				.toList();
	}

	@Override
	public boolean supportsToolCalling(final ModelCategory modelCategory) {
		final var modelProfile = this.modelProfileService.getModelProfile(modelCategory.getModelProfile(), modelCategory);
		return this.supportsToolCalling(modelProfile);
	}

	@Override
	public boolean supportsToolCalling(final ModelProfile modelProfile) {
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.OPENROUTER) {
			return false;
		}
		final var metadata = this.resolveMetadata(modelProfile);
		return metadata != null && metadata.supportsToolCalling();
	}

	@Override
	public ModelProvider getModelProvider() {
		return ModelProvider.OPENROUTER;
	}

	@Override
	protected Completion requestCompletion(final Conversation conversation, final ModelProfile modelProfile, final Consumer<Chunk> chunkConsumer, final ToolCallData toolCallData) {
		final var effectiveModelProfile = this.toEffectiveModelProfile(modelProfile);
		LOGGER.debug("Requesting completion with OpenRouter model {} ", effectiveModelProfile.modelName());
		return super.requestCompletion(conversation, effectiveModelProfile, chunkConsumer, toolCallData);
	}

	@Override
	protected boolean sendUsage(final ModelProfile modelProfile) {
		return false;
	}

	@Override
	protected boolean sendReasoning(final ModelProfile modelProfile) {
		final var metadata = this.resolveMetadata(modelProfile);
		return metadata != null && metadata.supportsReasoning();
	}

	@Override
	protected OpenAiProviderDialect.ChatTokenLimitParameterMode chatTokenLimitParameterMode(final ModelProfile modelProfile) {
		final var metadata = this.resolveMetadata(modelProfile);
		return metadata == null ? OpenAiProviderDialect.ChatTokenLimitParameterMode.MAX_COMPLETION_TOKENS : metadata.tokenLimitParameterMode();
	}

	private OpenRouterModelDiscovery.ResolvedOpenRouterModelMetadata resolveMetadata(final ModelProfile modelProfile) {
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.OPENROUTER) {
			return null;
		}
		var modelName = modelProfile.modelName();
		if (modelName == null || modelName.isBlank()) {
			modelName = this.openRouterModelDiscovery.listModels(modelProfile, new OpenRouterModelQuery(true, null, List.of(), List.of()), false).stream()
				.filter(model -> model.getPricing() != null && "0".equals(model.getPricing().getPrompt()) && "0".equals(model.getPricing().getCompletion())) // Prefer free models if no model name specified
				.map(OpenRouterModelsResponse.OpenRouterModel::getId)
				.filter(id -> id != null && !id.isBlank())
				.findFirst()
				.orElse("");
		}
		if (modelName.isBlank()) {
			return null;
		}
		return this.openRouterModelDiscovery.resolveModelMetadata(modelProfile, modelName, false);
	}

	private ModelProfile toEffectiveModelProfile(final ModelProfile modelProfile) {
		final var metadata = this.resolveMetadata(modelProfile);
		if (metadata == null || metadata.modelId() == null || metadata.modelId().isBlank()) {
			return modelProfile;
		}
		final var promptCost = metadata.promptCost();
		final var cachedPromptCost = metadata.cachedPromptCost();
		final var completionCost = metadata.completionCost();
		final var reasoningCost = metadata.reasoningCost();
		int maxContextLength;
		if (modelProfile.maxContextLength() <= 0 && metadata.contextLength() <= 0) {
			throw new IllegalArgumentException("OpenRouter metadata don't specify a valid context length. Therefore the model profile must specify a valid context length.");
		}
		else if (modelProfile.maxContextLength() > 0 && metadata.contextLength() > 0) {
			maxContextLength = Math.min(modelProfile.maxContextLength(), metadata.contextLength());
		}
		else {
			maxContextLength = modelProfile.maxContextLength() > 0 ? modelProfile.maxContextLength() : metadata.contextLength();
		}
		int maxTokens;
		if (modelProfile.maxTokens() <= 0 && metadata.maxCompletionTokens() <= 0) {
			throw new IllegalArgumentException("OpenRouter metadata don't specify a valid max completion tokens. Therefore the model profile must specify a valid max tokens.");
		}
		else if (modelProfile.maxTokens() > 0 && metadata.maxCompletionTokens() > 0) {
			maxTokens = Math.min(modelProfile.maxTokens(), metadata.maxCompletionTokens());
		}
		else {
			maxTokens = modelProfile.maxTokens() > 0 ? modelProfile.maxTokens() : metadata.maxCompletionTokens();
		}
		return new ModelProfile(
				modelProfile.prompt(),
				modelProfile.tokenizer(),
				modelProfile.tokenEncoding(),
				maxTokens,
				maxContextLength,
				modelProfile.temperature(),
				modelProfile.topP(),
				ModelProvider.OPENROUTER,
				metadata.modelId(),
				OpenRouterModelDiscovery.OPENROUTER_API_URL,
				modelProfile.apiKey(),
				promptCost != null ? promptCost : modelProfile.costPerInToken(),
				cachedPromptCost != null ? cachedPromptCost : modelProfile.costPerCachedInToken(),
				completionCost != null ? completionCost : modelProfile.costPerOutToken(),
				reasoningCost != null ? reasoningCost : modelProfile.costPerReasoningOutToken(),
				modelProfile.category());
	}

}