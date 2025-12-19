package de.extio.lmlib.client.oai;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

class ModelNameLoader {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ModelNameLoader.class);
	
	private final RestClient restClient;
	
	public ModelNameLoader(final RestClient restClient) {
		this.restClient = restClient;
	}
	
	public Optional<String> loadModelName() {
		try {
			LOGGER.info("Querying model name");
			
			final var modelName = this.restClient
					.method(HttpMethod.GET)
					.uri(uriBuilder -> uriBuilder.path("/v1/models").build())
					.retrieve()
					.body(ModelsResponse.class)
					.getData()
					.stream()
					.map(Model::getId)
					.findFirst();
			
			modelName.ifPresent(name -> LOGGER.info("Model name: {}", name));
			return modelName;
		}
		catch (final Exception e) {
			LOGGER.warn("Failed to load model name: {}", e.getMessage());
			return Optional.empty();
		}
	}
	
}
