package de.extio.lmlib.client.oai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;

@Component
public class ModelNameSupplier {
	
	@Autowired
	@Qualifier("lmLibWebClientBuilder")
	private WebClient.Builder webClientBuilder;
	
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
			final var webClient = this.webClientBuilder.baseUrl(modelProfile.url()).build();
			final var loader = new ModelNameLoader(webClient);
			return loader.loadModelName().orElse("");
		});
	}
	
}
