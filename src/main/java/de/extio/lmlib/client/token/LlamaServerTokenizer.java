package de.extio.lmlib.client.token;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;

import de.extio.lmlib.client.profile.ModelProfile;

import reactor.util.retry.Retry;

final class LlamaServerTokenizer implements Tokenizer {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(LlamaServerTokenizer.class);
	
	private final WebClient.Builder webClientBuilder;
	
	public LlamaServerTokenizer(final Builder webClientBuilder) {
		this.webClientBuilder = webClientBuilder;
	}
	
	@Override
	public List<Long> tokenize(final String txt, final ModelProfile modelProfile) {
		LOGGER.debug("Requesting tokenizing");
		final var request = new TokenizeRequest(txt);
		final var webClient = this.webClientBuilder.baseUrl(modelProfile.url()).build();
		final var response = webClient
				.method(HttpMethod.POST)
				.uri(uriBuilder -> uriBuilder.path("/tokenize").build())
				.bodyValue(request)
				.retrieve()
				.bodyToMono(TokenizeResponse.class)
				.retryWhen(Retry.backoff(99, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(10)).jitter(0.25d).doAfterRetry(rs -> LOGGER.warn("Request failed " + rs.failure().getMessage())))
				.block();
		return response.tokens();
	}
	
	@Override
	public int count(final String txt, final ModelProfile modelProfile) {
		return this.tokenize(txt, modelProfile).size();
	}
	
	@Override
	public String detokenize(final List<Long> tokens, final ModelProfile modelProfile) {
		LOGGER.debug("Requesting detokenizing");
		final var request = new DetokenizeRequest(tokens);
		final var webClient = this.webClientBuilder.baseUrl(modelProfile.url()).build();
		final var response = webClient
				.method(HttpMethod.POST)
				.uri(uriBuilder -> uriBuilder.path("/detokenize").build())
				.bodyValue(request)
				.retrieve()
				.bodyToMono(DetokenizeResponse.class)
				.retryWhen(Retry.backoff(99, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(10)).jitter(0.25d).doAfterRetry(rs -> LOGGER.warn("Request failed " + rs.failure().getMessage())))
				.block();
		return response.content();
	}
	
	private record TokenizeRequest(String content) {
	}
	
	private record TokenizeResponse(List<Long> tokens) {
	}
	
	private record DetokenizeRequest(List<Long> tokens) {
	}
	
	private record DetokenizeResponse(String content) {
	}
	
}
