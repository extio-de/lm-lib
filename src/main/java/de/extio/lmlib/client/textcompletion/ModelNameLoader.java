package de.extio.lmlib.client.textcompletion;

import java.time.Duration;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import de.extio.lmlib.client.textcompletion.dto.Model;
import de.extio.lmlib.client.textcompletion.dto.ModelsResponse;

import reactor.util.retry.Retry;

class ModelNameLoader implements Runnable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ModelNameLoader.class);
	
	private final WebClient webClient;
	
	private final Consumer<String> consumer;
	
	public ModelNameLoader(@Qualifier("textCompletionWebClientBuilder") final WebClient webClient, final Consumer<String> consumer) {
		this.consumer = consumer;
		this.webClient = webClient;
	}
	
	@Override
	public void run() {
		String modelName = null;
		do {
			try {
				LOGGER.info("Querying model name");
				
				modelName = this.webClient
						.method(HttpMethod.GET)
						.uri(uriBuilder -> uriBuilder.path("/v1/models").build())
						.retrieve()
						.bodyToMono(ModelsResponse.class)
						.retryWhen(Retry.backoff(99, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(10)).jitter(0.25d).doAfterRetry(rs -> LOGGER.warn("Request failed", rs.failure())))
						.block()
						.getData()
						.stream()
						.map(Model::getId)
						.findFirst()
						.orElseThrow();
			}
			catch (final Exception e) {
				LOGGER.error(e.getMessage(), e);
				try {
					Thread.sleep(1000l);
				}
				catch (final InterruptedException e1) {
					throw new RuntimeException("Interrupted", e1);
				}
			}
		} while (modelName == null);
		
		LOGGER.info("Model name: {}", modelName);
		this.consumer.accept(modelName);
	}
	
}
