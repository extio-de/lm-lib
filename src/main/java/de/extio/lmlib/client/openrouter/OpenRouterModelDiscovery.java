package de.extio.lmlib.client.openrouter;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import de.extio.lmlib.client.oai.OpenAiProviderDialect;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;

public class OpenRouterModelDiscovery {

	static final String OPENROUTER_API_URL = "https://openrouter.ai/api";

	private static final Logger LOGGER = LoggerFactory.getLogger(OpenRouterModelDiscovery.class);

	@Autowired
	@Qualifier("lmLibRestClientBuilder")
	private RestClient.Builder restClientBuilder;

	private final Map<String, List<OpenRouterModelsResponse.OpenRouterModel>> modelListCache = new ConcurrentHashMap<>();

	private final Map<String, OpenRouterModelEndpointsResponse.OpenRouterModelEndpoints> endpointCache = new ConcurrentHashMap<>();

	private final Map<String, ResolvedOpenRouterModelMetadata> metadataCache = new ConcurrentHashMap<>();

	public List<OpenRouterModelsResponse.OpenRouterModel> listModels(final ModelProfile modelProfile, final OpenRouterModelQuery query_, final boolean forceReload) {
		this.validateProfile(modelProfile);
		final var query = query_ == null ? new OpenRouterModelQuery(false, null, List.of(), List.of()) : query_;
		if (query.userFiltered() && query.hasServerSideFilters()) {
			throw new IllegalArgumentException("OpenRouter /models/user does not support category, supported_parameters, or output_modalities filters");
		}
		final var cacheKey = this.modelListCacheKey(modelProfile, query);
		if (forceReload) {
			this.modelListCache.remove(cacheKey);
		}
		return this.modelListCache.computeIfAbsent(cacheKey, key -> this.loadModels(modelProfile, query));
	}
	
	public OpenRouterModelEndpointsResponse.OpenRouterModelEndpoints getModelEndpoints(final ModelProfile modelProfile, final String modelName, final boolean forceReload) {
		this.validateProfile(modelProfile);
		if (modelName == null || modelName.isBlank()) {
			return new OpenRouterModelEndpointsResponse.OpenRouterModelEndpoints();
		}
		final var cacheKey = this.endpointCacheKey(modelProfile, modelName);
		if (forceReload) {
			final var endpoints = this.loadModelEndpoints(modelProfile, modelName, true);
			this.endpointCache.put(cacheKey, endpoints);
			return endpoints;
		}
		return this.endpointCache.computeIfAbsent(cacheKey, key -> this.loadModelEndpoints(modelProfile, modelName, false));
	}

	public ResolvedOpenRouterModelMetadata resolveModelMetadata(final ModelProfile modelProfile, final String modelName_, final boolean forceReload) {
		this.validateProfile(modelProfile);
		final var modelName = modelName_ == null || modelName_.isBlank() ? this.listModels(modelProfile, new OpenRouterModelQuery(true, null, List.of(), List.of()), forceReload)
				.stream()
				.map(OpenRouterModelsResponse.OpenRouterModel::getId)
				.filter(Objects::nonNull)
				.filter(id -> !id.isBlank())
				.findFirst()
				.orElse("") : modelName_;
		if (modelName.isBlank()) {
			return new ResolvedOpenRouterModelMetadata("", null, null, null, List.of(), 0, 0);
		}
		final var cacheKey = this.metadataCacheKey(modelProfile, modelName);
		if (forceReload) {
			final var metadata = this.loadResolvedMetadata(modelProfile, modelName, true);
			this.metadataCache.put(cacheKey, metadata);
			return metadata;
		}
		return this.metadataCache.computeIfAbsent(cacheKey, key -> this.loadResolvedMetadata(modelProfile, modelName, false));
	}

	private List<OpenRouterModelsResponse.OpenRouterModel> loadModels(final ModelProfile modelProfile, final OpenRouterModelQuery query) {
		try {
			var requestSpec = this.restClientBuilder.baseUrl(OPENROUTER_API_URL).build()
					.method(HttpMethod.GET)
					.uri(uriBuilder -> {
						uriBuilder.path(query.userFiltered() ? "/v1/models/user" : "/v1/models");
						if (!query.userFiltered() && query.category() != null && !query.category().isBlank()) {
							uriBuilder.queryParam("category", query.category());
						}
						final var supportedParameters = this.join(query.supportedParameters());
						if (!query.userFiltered() && !supportedParameters.isBlank()) {
							uriBuilder.queryParam("supported_parameters", supportedParameters);
						}
						final var outputModalities = this.join(query.outputModalities());
						if (!query.userFiltered() && !outputModalities.isBlank()) {
							uriBuilder.queryParam("output_modalities", outputModalities);
						}
						return uriBuilder.build();
					});
			if (modelProfile.apiKey() != null && !modelProfile.apiKey().isBlank()) {
				requestSpec = requestSpec.header("Authorization", "Bearer " + modelProfile.apiKey());
			}
			final var response = requestSpec.retrieve().body(OpenRouterModelsResponse.class);
			return response == null || response.getData() == null ? List.of() : List.copyOf(response.getData());
		}
		catch (final RestClientResponseException e) {
			LOGGER.warn("Failed to load OpenRouter models: {} {}", e.getStatusCode(), e.getStatusText());
			return List.of();
		}
		catch (final RestClientException e) {
			LOGGER.warn("Failed to load OpenRouter models: {}", e.getMessage());
			return List.of();
		}
		catch (final RuntimeException e) {
			LOGGER.warn("Failed to load OpenRouter models", e);
			return List.of();
		}
	}

	private OpenRouterModelEndpointsResponse.OpenRouterModelEndpoints loadModelEndpoints(final ModelProfile modelProfile, final String modelName, final boolean forceReload) {
		try {
			final var model = this.findModel(modelProfile, modelName, forceReload);
			var requestSpec = this.restClientBuilder.baseUrl(OPENROUTER_API_URL).build()
					.method(HttpMethod.GET)
					.uri(this.resolveDetailsPath(model, modelName));
			if (modelProfile.apiKey() != null && !modelProfile.apiKey().isBlank()) {
				requestSpec = requestSpec.header("Authorization", "Bearer " + modelProfile.apiKey());
			}
			final var response = requestSpec.retrieve().body(OpenRouterModelEndpointsResponse.class);
			return response == null || response.getData() == null ? new OpenRouterModelEndpointsResponse.OpenRouterModelEndpoints() : response.getData();
		}
		catch (final RestClientResponseException e) {
			if (e.getStatusCode().value() != 404) {
				LOGGER.warn("Failed to load OpenRouter endpoints for {}: {} {}", modelName, e.getStatusCode(), e.getStatusText());
			}
			return new OpenRouterModelEndpointsResponse.OpenRouterModelEndpoints();
		}
		catch (final RestClientException e) {
			LOGGER.warn("Failed to load OpenRouter endpoints for {}: {}", modelName, e.getMessage());
			return new OpenRouterModelEndpointsResponse.OpenRouterModelEndpoints();
		}
		catch (final RuntimeException e) {
			LOGGER.warn("Failed to load OpenRouter endpoints for {}", modelName, e);
			return new OpenRouterModelEndpointsResponse.OpenRouterModelEndpoints();
		}
	}

	private ResolvedOpenRouterModelMetadata loadResolvedMetadata(final ModelProfile modelProfile, final String modelName, final boolean forceReload) {
		final var model = this.findModel(modelProfile, modelName, forceReload);
		final var endpoints = this.getModelEndpoints(modelProfile, modelName, forceReload);
		final var endpoint = this.preferredEndpoint(endpoints);
		final var pricing = endpoint != null && this.hasPricing(endpoint.getPricing()) ? endpoint.getPricing() : model != null ? model.getPricing() : null;
		final List<String> supportedParameters = endpoint != null && endpoint.getSupportedParameters() != null && !endpoint.getSupportedParameters().isEmpty()
				? List.copyOf(endpoint.getSupportedParameters())
				: model != null && model.getSupportedParameters() != null ? List.copyOf(model.getSupportedParameters()) : List.of();
		final var contextLength = this.firstPositive(
				endpoint != null ? endpoint.getContextLength() : null,
				model != null && model.getTopProvider() != null ? model.getTopProvider().getContextLength() : null,
				model != null ? model.getContextLength() : null,
				model != null && model.getPerRequestLimits() != null ? model.getPerRequestLimits().getPromptTokens() : null);
		final var maxCompletionTokens = this.firstPositive(
				endpoint != null ? endpoint.getMaxCompletionTokens() : null,
				model != null && model.getTopProvider() != null ? model.getTopProvider().getMaxCompletionTokens() : null,
				model != null && model.getPerRequestLimits() != null ? model.getPerRequestLimits().getCompletionTokens() : null);
		return new ResolvedOpenRouterModelMetadata(modelName, model, endpoints, pricing, supportedParameters, contextLength, maxCompletionTokens);
	}

	private OpenRouterModelsResponse.OpenRouterModel findModel(final ModelProfile modelProfile, final String modelName, final boolean forceReload) {
		final var model = this.findMatchingModel(this.listModels(modelProfile, new OpenRouterModelQuery(true, null, List.of(), List.of()), forceReload), modelName);
		if (model != null) {
			return model;
		}
		return this.findMatchingModel(this.listModels(modelProfile, new OpenRouterModelQuery(false, null, List.of(), List.of()), forceReload), modelName);
	}

	private OpenRouterModelsResponse.OpenRouterModel findMatchingModel(final List<OpenRouterModelsResponse.OpenRouterModel> models, final String modelName) {
		if (modelName == null || modelName.isBlank() || models == null || models.isEmpty()) {
			return null;
		}
		return models.stream()
				.filter(Objects::nonNull)
				.filter(model -> modelName.equalsIgnoreCase(model.getId()) || modelName.equalsIgnoreCase(model.getCanonicalSlug()))
				.findFirst()
				.orElse(null);
	}

	private OpenRouterModelEndpointsResponse.OpenRouterEndpoint preferredEndpoint(final OpenRouterModelEndpointsResponse.OpenRouterModelEndpoints endpoints) {
		if (endpoints == null || endpoints.getEndpoints() == null || endpoints.getEndpoints().isEmpty()) {
			return null;
		}
		return endpoints.getEndpoints().stream()
				.filter(Objects::nonNull)
				.filter(endpoint -> endpoint.getStatus() != null && endpoint.getStatus().equalsIgnoreCase("default"))
				.findFirst()
				.orElse(endpoints.getEndpoints().getFirst());
	}

	private String resolveDetailsPath(final OpenRouterModelsResponse.OpenRouterModel model, final String modelName) {
		if (model != null && model.getLinks() != null && model.getLinks().getDetails() != null && !model.getLinks().getDetails().isBlank()) {
			return model.getLinks().getDetails();
		}
		final var separator = modelName.indexOf('/');
		if (separator < 0 || separator == modelName.length() - 1) {
			return "/v1/models";
		}
		return "/v1/models/" + this.encode(modelName.substring(0, separator)) + "/" + this.encode(modelName.substring(separator + 1)) + "/endpoints";
	}

	private String encode(final String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private int firstPositive(final Integer... values) {
		for (final var value : values) {
			if (value != null && value > 0) {
				return value;
			}
		}
		return 0;
	}

	private boolean hasPricing(final OpenRouterModelsResponse.OpenRouterPricing pricing) {
		return pricing != null
				&& ((pricing.getPrompt() != null && !pricing.getPrompt().isBlank())
						|| (pricing.getCompletion() != null && !pricing.getCompletion().isBlank())
						|| (pricing.getInternalReasoning() != null && !pricing.getInternalReasoning().isBlank())
						|| (pricing.getInputCacheRead() != null && !pricing.getInputCacheRead().isBlank())
						|| (pricing.getInputCacheWrite() != null && !pricing.getInputCacheWrite().isBlank()));
	}

	private String join(final List<String> values) {
		if (values == null || values.isEmpty()) {
			return "";
		}
		return values.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.collect(Collectors.joining(","));
	}

	private String modelListCacheKey(final ModelProfile modelProfile, final OpenRouterModelQuery query) {
		return this.baseKey(modelProfile) + "|user=" + query.userFiltered() + "|category=" + Objects.toString(query.category(), "") + "|supported=" + this.join(query.supportedParameters()) + "|modalities=" + this.join(query.outputModalities());
	}

	private String endpointCacheKey(final ModelProfile modelProfile, final String modelName) {
		return this.baseKey(modelProfile) + "|endpoints|" + modelName;
	}

	private String metadataCacheKey(final ModelProfile modelProfile, final String modelName) {
		return this.baseKey(modelProfile) + "|metadata|" + modelName;
	}

	private String baseKey(final ModelProfile modelProfile) {
		return modelProfile.category() + "|" + OPENROUTER_API_URL + "|" + modelProfile.apiKey();
	}

	private void validateProfile(final ModelProfile modelProfile) {
		if (modelProfile == null || modelProfile.modelProvider() != ModelProvider.OPENROUTER) {
			throw new IllegalArgumentException("Invalid OpenRouter ModelProfile");
		}
	}

	static BigDecimal parsePricing(final String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return new BigDecimal(value).divide(new BigDecimal(1000000.0));
		}
		catch (final NumberFormatException e) {
			return null;
		}
	}

	public record ResolvedOpenRouterModelMetadata(
			String modelId,
			OpenRouterModelsResponse.OpenRouterModel model,
			OpenRouterModelEndpointsResponse.OpenRouterModelEndpoints endpoints,
			OpenRouterModelsResponse.OpenRouterPricing pricing,
			List<String> supportedParameters,
			int contextLength,
			int maxCompletionTokens) {

		public ResolvedOpenRouterModelMetadata {
			supportedParameters = supportedParameters == null ? List.of() : List.copyOf(supportedParameters);
		}

		public boolean supportsParameter(final String parameter) {
			if (parameter == null || parameter.isBlank()) {
				return false;
			}
			return this.supportedParameters.stream()
					.filter(Objects::nonNull)
					.map(value -> value.toLowerCase(Locale.ROOT).trim())
					.anyMatch(value -> value.equals(parameter.toLowerCase(Locale.ROOT)));
		}

		public boolean supportsToolCalling() {
			return this.supportsParameter("tools");
		}

		public boolean supportsReasoning() {
			return this.supportsParameter("reasoning") || this.supportsParameter("reasoning_effort") || this.supportsParameter("include_reasoning");
		}

		public OpenAiProviderDialect.ChatTokenLimitParameterMode tokenLimitParameterMode() {
			if (this.supportsParameter("max_completion_tokens")) {
				return OpenAiProviderDialect.ChatTokenLimitParameterMode.MAX_COMPLETION_TOKENS;
			}
			if (this.supportsParameter("max_tokens")) {
				return OpenAiProviderDialect.ChatTokenLimitParameterMode.MAX_TOKENS;
			}
			return OpenAiProviderDialect.ChatTokenLimitParameterMode.MAX_COMPLETION_TOKENS;
		}

		public BigDecimal promptCost() {
			return OpenRouterModelDiscovery.parsePricing(this.pricing != null ? this.pricing.getPrompt() : null);
		}

		public BigDecimal completionCost() {
			return OpenRouterModelDiscovery.parsePricing(this.pricing != null ? this.pricing.getCompletion() : null);
		}

		public BigDecimal cachedPromptCost() {
			if (this.pricing == null) {
				return null;
			}
			final var inputCacheReadCost = OpenRouterModelDiscovery.parsePricing(this.pricing.getInputCacheRead());
			return inputCacheReadCost != null ? inputCacheReadCost : OpenRouterModelDiscovery.parsePricing(this.pricing.getInputCacheWrite());
		}

		public BigDecimal reasoningCost() {
			return OpenRouterModelDiscovery.parsePricing(this.pricing != null ? this.pricing.getInternalReasoning() : null);
		}

		public BigDecimal requestCost() {
			return OpenRouterModelDiscovery.parsePricing(this.pricing != null ? this.pricing.getRequest() : null);
		}
	}

}