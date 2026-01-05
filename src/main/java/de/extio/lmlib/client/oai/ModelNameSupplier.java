package de.extio.lmlib.client.oai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClient;

import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;

public class ModelNameSupplier {
	
	@Autowired
	@Qualifier("lmLibRestClientBuilder")
	private RestClient.Builder restClientBuilder;
	
	private final Map<String, String> resolvedModelNames = new ConcurrentHashMap<>();
	
	public String getModelName(final ModelProfile modelProfile) {
		if (modelProfile.modelName() != null && !modelProfile.modelName().isBlank()) {
			return modelProfile.modelName();
		}
		
		if (modelProfile.modelProvider() != ModelProvider.OAI_TEXT_COMPLETION && modelProfile.modelProvider() != ModelProvider.OAI_CHAT_COMPLETION) {
			return "";
		}
		
		final var cacheKey = modelProfile.category() + "|" + modelProfile.url();
		return this.resolvedModelNames.computeIfAbsent(cacheKey, key -> {
			final var restClient = this.restClientBuilder.baseUrl(modelProfile.url()).build();
			final var loader = new ModelNameLoader(restClient);
			return loader.loadModelName().orElse("");
		});
	}
	
}
