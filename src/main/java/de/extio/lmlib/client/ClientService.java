package de.extio.lmlib.client;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import de.extio.lmlib.client.cached.CachedClient;
import de.extio.lmlib.client.cached.CachedClientRepository;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;
import de.extio.lmlib.profile.ModelProfileService;

@Service
public class ClientService {
	
	@Autowired
	private List<Client> clients;
	
	@Autowired
	private ModelProfileService modelProfileService;
	
	@Autowired(required = false)
	private CachedClientRepository cachedClientRepository;
	
	@Autowired(required = false)
	private List<CompletionInterceptor> completionInterceptors;
	
	@Cacheable("clients")
	public Client getClient(final ModelCategory category) {
		final var modelProfile = this.modelProfileService.getModelProfile(category.getModelProfile());
		return this.clients.stream()
				.filter(client -> client.getModelProvider() == modelProfile.modelProvider())
				.findFirst()
				.map(client -> this.cachedClientRepository == null ? client : new CachedClient(this.cachedClientRepository, this.modelProfileService, client))
				.map(client -> this.completionInterceptors == null || this.completionInterceptors.isEmpty() ? client : new InterceptingClient(client, this.completionInterceptors))
				.orElseThrow(() -> new IllegalStateException("No client found for model profile " + category));
	}
	
	private static class InterceptingClient implements Client {
		
		private final Client client;
		
		private final List<CompletionInterceptor> completionInterceptors;
		
		public InterceptingClient(final Client client, final List<CompletionInterceptor> completionInterceptors) {
			this.client = client;
			this.completionInterceptors = completionInterceptors;
		}
		
		@Override
		public Completion completion(final ModelCategory modelCategory, final String system, final String text) {
			return this.client.completion(modelCategory, system, text);
		}
		
		@Override
		public Completion conversation(final ModelCategory modelCategory, final Conversation conversation_) {
			Conversation conversation = conversation_;
			for (final var interceptor : this.completionInterceptors) {
				conversation = interceptor.before(modelCategory, conversation);
			}
			var completion = this.client.conversation(modelCategory, conversation);
			for (final var interceptor : this.completionInterceptors) {
				completion = interceptor.after(modelCategory, completion);
			}
			return completion;
		}

		@Override
		public Completion streamConversation(final ModelCategory modelCategory, final Conversation conversation_, final Consumer<String> chunkConsumer) {
			Conversation conversation = conversation_;
			for (final var interceptor : this.completionInterceptors) {
				conversation = interceptor.before(modelCategory, conversation);
			}
			var completion = this.client.streamConversation(modelCategory, conversation, chunkConsumer);
			for (final var interceptor : this.completionInterceptors) {
				completion = interceptor.after(modelCategory, completion);
			}
			return completion;
		}
		
		@Override
		public ModelProvider getModelProvider() {
			return this.client.getModelProvider();
		}
		
	}
	
}
