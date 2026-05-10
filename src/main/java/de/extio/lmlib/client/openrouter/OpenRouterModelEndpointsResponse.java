package de.extio.lmlib.client.openrouter;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class OpenRouterModelEndpointsResponse {

	private OpenRouterModelEndpoints data;

	@JsonProperty("data")
	public OpenRouterModelEndpoints getData() {
		return this.data;
	}

	public void setData(final OpenRouterModelEndpoints data) {
		this.data = data;
	}

	public static final class OpenRouterModelEndpoints {

		private String id;

		private String name;

		private Integer created;

		private String description;

		private OpenRouterModelsResponse.OpenRouterModelArchitecture architecture;

		private List<OpenRouterEndpoint> endpoints;

		@JsonProperty("id")
		public String getId() {
			return this.id;
		}

		public void setId(final String id) {
			this.id = id;
		}

		@JsonProperty("name")
		public String getName() {
			return this.name;
		}

		public void setName(final String name) {
			this.name = name;
		}

		@JsonProperty("created")
		public Integer getCreated() {
			return this.created;
		}

		public void setCreated(final Integer created) {
			this.created = created;
		}

		@JsonProperty("description")
		public String getDescription() {
			return this.description;
		}

		public void setDescription(final String description) {
			this.description = description;
		}

		@JsonProperty("architecture")
		public OpenRouterModelsResponse.OpenRouterModelArchitecture getArchitecture() {
			return this.architecture;
		}

		public void setArchitecture(final OpenRouterModelsResponse.OpenRouterModelArchitecture architecture) {
			this.architecture = architecture;
		}

		@JsonProperty("endpoints")
		public List<OpenRouterEndpoint> getEndpoints() {
			return this.endpoints;
		}

		public void setEndpoints(final List<OpenRouterEndpoint> endpoints) {
			this.endpoints = endpoints;
		}
	}

	public static final class OpenRouterEndpoint {

		private String name;

		private String modelId;

		private String modelName;

		private String providerName;

		private String status;

		private String tag;

		private Integer contextLength;

		private Integer maxCompletionTokens;

		private Integer maxPromptTokens;

		private OpenRouterModelsResponse.OpenRouterPricing pricing;

		private List<String> supportedParameters;

		private boolean supportsImplicitCaching;

		@JsonProperty("name")
		public String getName() {
			return this.name;
		}

		public void setName(final String name) {
			this.name = name;
		}

		@JsonProperty("model_id")
		public String getModelId() {
			return this.modelId;
		}

		public void setModelId(final String modelId) {
			this.modelId = modelId;
		}

		@JsonProperty("model_name")
		public String getModelName() {
			return this.modelName;
		}

		public void setModelName(final String modelName) {
			this.modelName = modelName;
		}

		@JsonProperty("provider_name")
		public String getProviderName() {
			return this.providerName;
		}

		public void setProviderName(final String providerName) {
			this.providerName = providerName;
		}

		@JsonProperty("status")
		public String getStatus() {
			return this.status;
		}

		public void setStatus(final String status) {
			this.status = status;
		}

		@JsonProperty("tag")
		public String getTag() {
			return this.tag;
		}

		public void setTag(final String tag) {
			this.tag = tag;
		}

		@JsonProperty("context_length")
		public Integer getContextLength() {
			return this.contextLength;
		}

		public void setContextLength(final Integer contextLength) {
			this.contextLength = contextLength;
		}

		@JsonProperty("max_completion_tokens")
		public Integer getMaxCompletionTokens() {
			return this.maxCompletionTokens;
		}

		public void setMaxCompletionTokens(final Integer maxCompletionTokens) {
			this.maxCompletionTokens = maxCompletionTokens;
		}

		@JsonProperty("max_prompt_tokens")
		public Integer getMaxPromptTokens() {
			return this.maxPromptTokens;
		}

		public void setMaxPromptTokens(final Integer maxPromptTokens) {
			this.maxPromptTokens = maxPromptTokens;
		}

		@JsonProperty("pricing")
		public OpenRouterModelsResponse.OpenRouterPricing getPricing() {
			return this.pricing;
		}

		public void setPricing(final OpenRouterModelsResponse.OpenRouterPricing pricing) {
			this.pricing = pricing;
		}

		@JsonProperty("supported_parameters")
		public List<String> getSupportedParameters() {
			return this.supportedParameters;
		}

		public void setSupportedParameters(final List<String> supportedParameters) {
			this.supportedParameters = supportedParameters;
		}

		@JsonProperty("supports_implicit_caching")
		public boolean isSupportsImplicitCaching() {
			return this.supportsImplicitCaching;
		}

		public void setSupportsImplicitCaching(final boolean supportsImplicitCaching) {
			this.supportsImplicitCaching = supportsImplicitCaching;
		}
	}

}