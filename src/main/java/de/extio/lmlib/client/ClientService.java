package de.extio.lmlib.client;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;

import de.extio.lmlib.client.cached.CachedClient;
import de.extio.lmlib.client.cached.CachedClientRepository;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;
import de.extio.lmlib.profile.ModelProfileService;

public class ClientService {
	
	@Autowired
	private List<Client> clients;
	
	@Autowired
	private ModelProfileService modelProfileService;
	
	@Autowired(required = false)
	private CachedClientRepository cachedClientRepository;
	
	@Autowired(required = false)
	private List<CompletionInterceptor> completionInterceptors;
	
	public Client getClient(final ModelCategory category) {
		final var modelProfile = this.modelProfileService.getModelProfile(category.getModelProfile(), category);
		return this.getClient(modelProfile);
	}

	public List<String> getModelNames(final ModelCategory category, final boolean forceReload) {
		final var modelProfile = this.modelProfileService.getModelProfile(category.getModelProfile(), category);
		return this.getModelNames(modelProfile, forceReload);
	}

	public boolean supportsToolCalling(final ModelCategory category) {
		return this.getClient(category).supportsToolCalling();
	}
	
	@Cacheable("clients")
	public Client getClient(final ModelProfile modelProfile) {
		return this.clients.stream()
				.filter(client -> client.getModelProvider() == modelProfile.modelProvider())
				.findFirst()
				.map(client -> this.cachedClientRepository == null ? client : new CachedClient(this.cachedClientRepository, this.modelProfileService, client))
				.map(client -> this.completionInterceptors == null || this.completionInterceptors.isEmpty() ? client : new InterceptingClient(client, this.modelProfileService, this.completionInterceptors))
				.orElseThrow(() -> new IllegalStateException("No client found for model profile " + modelProfile.modelName()));
	}

	public List<String> getModelNames(final ModelProfile modelProfile, final boolean forceReload) {
		return this.getClient(modelProfile).getModelNames(modelProfile, forceReload);
	}

	public boolean supportsToolCalling(final ModelProfile modelProfile) {
		return this.getClient(modelProfile).supportsToolCalling();
	}
	
	private static class InterceptingClient implements Client {
		
		private final Client client;

		private final ModelProfileService modelProfileService;
		
		private final List<CompletionInterceptor> completionInterceptors;
		
		public InterceptingClient(final Client client, final ModelProfileService modelProfileService, final List<CompletionInterceptor> completionInterceptors) {
			this.client = client;
			this.modelProfileService = modelProfileService;
			this.completionInterceptors = completionInterceptors;
		}
		
		@Override
		public Completion conversation(final ModelCategory modelCategory, final Conversation conversation_, final ToolCallData toolCallData, final boolean skipCache) {
			final var modelProfile = this.modelProfileService.getModelProfile(modelCategory.getModelProfile(), modelCategory);
			return this.conversation(modelProfile, conversation_, toolCallData, skipCache);
		}
		
		@Override
		public Completion streamConversation(final ModelCategory modelCategory, final Conversation conversation_, final Consumer<Chunk> chunkConsumer, final ToolCallData toolCallData, final boolean skipCache) {
			final var modelProfile = this.modelProfileService.getModelProfile(modelCategory.getModelProfile(), modelCategory);
			return this.streamConversation(modelProfile, conversation_, chunkConsumer, toolCallData, skipCache);
		}

		@Override
		public Completion conversation(final ModelProfile modelProfile, final Conversation conversation_, final ToolCallData toolCallData, final boolean skipCache) {
			Conversation conversation = conversation_;
			for (final var interceptor : this.completionInterceptors) {
				conversation = interceptor.before(modelProfile, conversation);
			}
			var completion = this.client.conversation(modelProfile, conversation, toolCallData, skipCache);
			for (final var interceptor : this.completionInterceptors) {
				completion = interceptor.after(modelProfile, conversation, completion);
			}
			return completion;
		}
		
		@Override
		public Completion streamConversation(final ModelProfile modelProfile, final Conversation conversation_, final Consumer<Chunk> chunkConsumer, final ToolCallData toolCallData, final boolean skipCache) {
			Conversation conversation = conversation_;
			for (final var interceptor : this.completionInterceptors) {
				conversation = interceptor.before(modelProfile, conversation);
			}
			var completion = this.client.streamConversation(modelProfile, conversation, chunkConsumer, toolCallData, skipCache);
			for (final var interceptor : this.completionInterceptors) {
				completion = interceptor.after(modelProfile, conversation, completion);
			}
			return completion;
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
		
	}
	
}
