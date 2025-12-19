package de.extio.lmlib.token;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import de.extio.lmlib.profile.ModelProfile;

final class LlamaServerTokenizer implements Tokenizer {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(LlamaServerTokenizer.class);
	
	private final RestClient.Builder restClientBuilder;
	
	public LlamaServerTokenizer(final RestClient.Builder restClientBuilder) {
		this.restClientBuilder = restClientBuilder;
	}
	
	@Override
	public List<Long> tokenize(final String txt, final ModelProfile modelProfile) {
		LOGGER.debug("Requesting tokenizing");
		final var request = new TokenizeRequest(txt);
		final var restClient = this.restClientBuilder.baseUrl(modelProfile.url()).build();
		final var response = restClient
				.method(HttpMethod.POST)
				.uri("/tokenize")
				.body(request)
				.retrieve()
				.body(TokenizeResponse.class);
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
		final var restClient = this.restClientBuilder.baseUrl(modelProfile.url()).build();
		final var response = restClient
				.method(HttpMethod.POST)
				.uri("/detokenize")
				.body(request)
				.retrieve()
				.body(DetokenizeResponse.class);
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
