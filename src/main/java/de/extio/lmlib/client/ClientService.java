package de.extio.lmlib.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.extio.lmlib.client.profile.ModelCategory;
import de.extio.lmlib.client.profile.ModelProfileService;

@Service
public class ClientService {
	
	@Autowired
	private List<Client> clients;
	
	@Autowired
	private ModelProfileService modelProfileService;
	
	public Client getClient(final ModelCategory category) {
		final var modelProfile = this.modelProfileService.getModelProfile(category.getModelProfile());
		return this.clients.stream()
				.filter(client -> client.getModelProvider() == modelProfile.modelProvider())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No client found for model profile " + category));
	}
	
}
