package de.extio.lmlib.token;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.extio.lmlib.client.ProxyAuthorizationSupport;
import de.extio.lmlib.profile.ModelProfile;

/**
 * {@link Tokenizer} implementation backed by the Anthropic token counting
 * endpoint ({@code POST /v1/messages/count_tokens}).
 * <p>
 * The API only supports counting, so {@link #tokenize(String, ModelProfile)}
 * and {@link #detokenize(List, ModelProfile)} work with a plain codepoint list
 * instead of real model tokens.
 */
final class AnthropicTokenizer implements Tokenizer {

	static final String NAME = "anthropic";

	private static final String COUNT_TOKENS_PATH = "/v1/messages/count_tokens";

	private static final String ANTHROPIC_VERSION = "2023-06-01";

	private static final Logger LOGGER = LoggerFactory.getLogger(AnthropicTokenizer.class);

	private final RestClient.Builder restClientBuilder;

	private final ProxyAuthorizationSupport proxyAuthorizationSupport;

	public AnthropicTokenizer(final RestClient.Builder restClientBuilder, final ProxyAuthorizationSupport proxyAuthorizationSupport) {
		this.restClientBuilder = restClientBuilder;
		this.proxyAuthorizationSupport = proxyAuthorizationSupport;
	}

	@Override
	public String getName() {
		return NAME;
	}

	/**
	 * Returns the codepoints of the given text, since the Anthropic token
	 * counting endpoint does not expose token ids.
	 */
	@Override
	public List<Long> tokenize(final String txt, final ModelProfile modelProfile) {
		if (txt == null || txt.isEmpty()) {
			return List.of();
		}
		return txt.codePoints().mapToObj(codePoint -> (long) codePoint).toList();
	}

	@Override
	public int count(final String txt, final ModelProfile modelProfile) {
		if (txt == null || txt.isEmpty()) {
			return 0;
		}
		LOGGER.debug("Requesting token count");
		final var modelName = modelProfile.modelName();
		if (modelName == null || modelName.isBlank()) {
			throw new IllegalStateException("Anthropic token counting requires a model name in the model profile");
		}
		final var url = modelProfile.url();
		final var baseUrl = stripCountTokensSuffix(url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
		final var restClient = this.restClientBuilder.baseUrl(baseUrl).build();
		var requestSpec = restClient
				.method(HttpMethod.POST)
				.uri(countTokensPath(url))
				.header("anthropic-version", ANTHROPIC_VERSION);
		if (modelProfile.apiKey() != null && !modelProfile.apiKey().isBlank()) {
			requestSpec = requestSpec.header("x-api-key", modelProfile.apiKey());
		}
		requestSpec = this.proxyAuthorizationSupport.apply(requestSpec);
		final var response = requestSpec
				.body(new CountTokensRequest(modelName, List.of(new CountTokensMessage("user", txt))))
				.retrieve()
				.onStatus(status -> {
					if (status.getStatusCode().isError()) {
						LOGGER.error("Token count request failed with status code {} {}: {}", status.getStatusCode(), status.getStatusText(), new String(status.getBody().readAllBytes(), StandardCharsets.UTF_8));
						throw new RuntimeException("Token count request failed with status code " + status.getStatusCode() + " " + status.getStatusText() + ". Your LLM provider may not support the /v1/messages/count_tokens endpoint");
					}
					return false;
				})
				.body(CountTokensResponse.class);
		if (response == null || response.inputTokens() == null) {
			throw new RuntimeException("Token count request failed: response is null. Your LLM provider may not support the /v1/messages/count_tokens endpoint");
		}
		return response.inputTokens();
	}

	/**
	 * Rebuilds the text from its codepoints, since the Anthropic token counting
	 * endpoint does not expose token ids.
	 */
	@Override
	public String detokenize(final List<Long> tokens, final ModelProfile modelProfile) {
		if (tokens == null || tokens.isEmpty()) {
			return "";
		}
		final var builder = new StringBuilder(tokens.size());
		for (final var token : tokens) {
			if (token != null) {
				builder.append(Character.toString(token.intValue()));
			}
		}
		return builder.toString();
	}

	/**
	 * Resolves the count tokens endpoint path relative to the configured base
	 * URL, tolerating URLs that already end in {@code /v1} or
	 * {@code /v1/messages}.
	 */
	static String countTokensPath(final String url) {
		if (url == null) {
			return COUNT_TOKENS_PATH;
		}
		if (url.endsWith("/v1/messages") || url.endsWith("/v1/messages/")) {
			return "/count_tokens";
		}
		if (url.endsWith("/v1/") || url.endsWith("/v1")) {
			return "/messages/count_tokens";
		}
		return COUNT_TOKENS_PATH;
	}

	/**
	 * Normalizes the configured base URL by removing a trailing
	 * {@code /v1/messages} endpoint suffix (the request path is appended
	 * separately).
	 */
	static String stripCountTokensSuffix(final String url) {
		if (url != null && url.endsWith("/v1/messages")) {
			return url.substring(0, url.length() - "/v1/messages".length());
		}
		return url;
	}

	private record CountTokensRequest(String model, List<CountTokensMessage> messages) {
	}

	private record CountTokensMessage(String role, String content) {
	}

	private record CountTokensResponse(@JsonProperty("input_tokens") Integer inputTokens) {
	}

}
