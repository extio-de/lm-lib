package de.extio.lmlib.client.oai;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

class ModelNameLoader {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ModelNameLoader.class);
	
	private final WebClient webClient;
	
	public ModelNameLoader(final WebClient webClient) {
		this.webClient = webClient;
	}
	
	public Optional<String> loadModelName() {
		try {
			LOGGER.info("Querying model name");
			
			final var modelName = this.webClient
					.method(HttpMethod.GET)
					.uri(uriBuilder -> uriBuilder.path("/v1/models").build())
					.retrieve()
					.bodyToMono(ModelsResponse.class)
					.block()
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
