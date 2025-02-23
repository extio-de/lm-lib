package de.extio.lmlib.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import de.extio.lmlib.client.cached.CachedClient;
import de.extio.lmlib.client.cached.CachedClientRepository;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfileService;

@Service
public class ClientService {
	
	@Autowired
	private List<Client> clients;
	
	@Autowired
	private ModelProfileService modelProfileService;
	
	@Autowired(required = false)
	private CachedClientRepository cachedClientRepository;
	
	@Cacheable("clients")
	public Client getClient(final ModelCategory category) {
		final var modelProfile = this.modelProfileService.getModelProfile(category.getModelProfile());
		return this.clients.stream()
				.filter(client -> client.getModelProvider() == modelProfile.modelProvider())
				.findFirst()
				.map(client -> this.cachedClientRepository == null ? client : new CachedClient(this.cachedClientRepository, this.modelProfileService, client))
				.orElseThrow(() -> new IllegalStateException("No client found for model profile " + category));
	}
	
}
