package de.extio.lmlib.client.openrouter;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class OpenRouterModelsResponse {
	
	private List<OpenRouterModel> data;
	
	@JsonProperty("data")
	public List<OpenRouterModel> getData() {
		return this.data;
	}
	
	public void setData(final List<OpenRouterModel> data) {
		this.data = data;
	}
	
	public static final class OpenRouterModel {
		
		private String id;
		
		private String canonicalSlug;
		
		private String name;
		
		private Integer created;
		
		private Integer contextLength;
		
		private OpenRouterModelArchitecture architecture;
		
		private OpenRouterPricing pricing;
		
		private List<String> supportedParameters;
		
		private OpenRouterTopProviderInfo topProvider;
		
		private OpenRouterPerRequestLimits perRequestLimits;
		
		private OpenRouterModelLinks links;
		
		private String description;
		
		private String knowledgeCutoff;
		
		private String expirationDate;
		
		@JsonProperty("id")
		public String getId() {
			return this.id;
		}
		
		public void setId(final String id) {
			this.id = id;
		}
		
		@JsonProperty("canonical_slug")
		public String getCanonicalSlug() {
			return this.canonicalSlug;
		}
		
		public void setCanonicalSlug(final String canonicalSlug) {
			this.canonicalSlug = canonicalSlug;
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
		
		@JsonProperty("context_length")
		public Integer getContextLength() {
			return this.contextLength;
		}
		
		public void setContextLength(final Integer contextLength) {
			this.contextLength = contextLength;
		}
		
		@JsonProperty("architecture")
		public OpenRouterModelArchitecture getArchitecture() {
			return this.architecture;
		}
		
		public void setArchitecture(final OpenRouterModelArchitecture architecture) {
			this.architecture = architecture;
		}
		
		@JsonProperty("pricing")
		public OpenRouterPricing getPricing() {
			return this.pricing;
		}
		
		public void setPricing(final OpenRouterPricing pricing) {
			this.pricing = pricing;
		}
		
		@JsonProperty("supported_parameters")
		public List<String> getSupportedParameters() {
			return this.supportedParameters;
		}
		
		public void setSupportedParameters(final List<String> supportedParameters) {
			this.supportedParameters = supportedParameters;
		}
		
		@JsonProperty("top_provider")
		public OpenRouterTopProviderInfo getTopProvider() {
			return this.topProvider;
		}
		
		public void setTopProvider(final OpenRouterTopProviderInfo topProvider) {
			this.topProvider = topProvider;
		}
		
		@JsonProperty("per_request_limits")
		public OpenRouterPerRequestLimits getPerRequestLimits() {
			return this.perRequestLimits;
		}
		
		public void setPerRequestLimits(final OpenRouterPerRequestLimits perRequestLimits) {
			this.perRequestLimits = perRequestLimits;
		}
		
		@JsonProperty("links")
		public OpenRouterModelLinks getLinks() {
			return this.links;
		}
		
		public void setLinks(final OpenRouterModelLinks links) {
			this.links = links;
		}
		
		@JsonProperty("description")
		public String getDescription() {
			return this.description;
		}
		
		public void setDescription(final String description) {
			this.description = description;
		}
		
		@JsonProperty("knowledge_cutoff")
		public String getKnowledgeCutoff() {
			return this.knowledgeCutoff;
		}
		
		public void setKnowledgeCutoff(final String knowledgeCutoff) {
			this.knowledgeCutoff = knowledgeCutoff;
		}
		
		@JsonProperty("expiration_date")
		public String getExpirationDate() {
			return this.expirationDate;
		}
		
		public void setExpirationDate(final String expirationDate) {
			this.expirationDate = expirationDate;
		}
		
		@Override
		public String toString() {
			return "OpenRouterModel [id=" + id + ", canonicalSlug=" + canonicalSlug + ", name=" + name + ", created=" + created + ", contextLength=" + contextLength + ", architecture=" + architecture + ", pricing=" + pricing + ", supportedParameters=" + supportedParameters + ", topProvider=" + topProvider + ", perRequestLimits=" + perRequestLimits + ", links=" + links + ", description=" + description + ", knowledgeCutoff=" + knowledgeCutoff + ", expirationDate=" + expirationDate + "]";
		}
		
	}
	
	public static final class OpenRouterModelArchitecture {
		
		private String tokenizer;
		
		private String instructType;
		
		private String modality;
		
		private List<String> inputModalities;
		
		private List<String> outputModalities;
		
		@JsonProperty("tokenizer")
		public String getTokenizer() {
			return this.tokenizer;
		}
		
		public void setTokenizer(final String tokenizer) {
			this.tokenizer = tokenizer;
		}
		
		@JsonProperty("instruct_type")
		public String getInstructType() {
			return this.instructType;
		}
		
		public void setInstructType(final String instructType) {
			this.instructType = instructType;
		}
		
		@JsonProperty("modality")
		public String getModality() {
			return this.modality;
		}
		
		public void setModality(final String modality) {
			this.modality = modality;
		}
		
		@JsonProperty("input_modalities")
		public List<String> getInputModalities() {
			return this.inputModalities;
		}
		
		public void setInputModalities(final List<String> inputModalities) {
			this.inputModalities = inputModalities;
		}
		
		@JsonProperty("output_modalities")
		public List<String> getOutputModalities() {
			return this.outputModalities;
		}
		
		public void setOutputModalities(final List<String> outputModalities) {
			this.outputModalities = outputModalities;
		}
		
		@Override
		public String toString() {
			return "OpenRouterModelArchitecture [tokenizer=" + tokenizer + ", instructType=" + instructType + ", modality=" + modality + ", inputModalities=" + inputModalities + ", outputModalities=" + outputModalities + "]";
		}
	}
	
	public static final class OpenRouterPricing {
		
		private String prompt;
		
		private String completion;
		
		private String internalReasoning;
		
		private String inputCacheRead;
		
		private String inputCacheWrite;
		
		private String request;
		
		private String image;
		
		@JsonProperty("prompt")
		public String getPrompt() {
			return this.prompt;
		}
		
		public void setPrompt(final String prompt) {
			this.prompt = prompt;
		}
		
		@JsonProperty("completion")
		public String getCompletion() {
			return this.completion;
		}
		
		public void setCompletion(final String completion) {
			this.completion = completion;
		}
		
		@JsonProperty("internal_reasoning")
		public String getInternalReasoning() {
			return this.internalReasoning;
		}
		
		public void setInternalReasoning(final String internalReasoning) {
			this.internalReasoning = internalReasoning;
		}
		
		@JsonProperty("input_cache_read")
		public String getInputCacheRead() {
			return this.inputCacheRead;
		}
		
		public void setInputCacheRead(final String inputCacheRead) {
			this.inputCacheRead = inputCacheRead;
		}
		
		@JsonProperty("input_cache_write")
		public String getInputCacheWrite() {
			return this.inputCacheWrite;
		}
		
		public void setInputCacheWrite(final String inputCacheWrite) {
			this.inputCacheWrite = inputCacheWrite;
		}
		
		@JsonProperty("request")
		public String getRequest() {
			return this.request;
		}
		
		public void setRequest(final String request) {
			this.request = request;
		}
		
		@JsonProperty("image")
		public String getImage() {
			return this.image;
		}
		
		public void setImage(final String image) {
			this.image = image;
		}
		
		@Override
		public String toString() {
			return "OpenRouterPricing [prompt=" + prompt + ", completion=" + completion + ", internalReasoning=" + internalReasoning + ", inputCacheRead=" + inputCacheRead + ", inputCacheWrite=" + inputCacheWrite + ", request=" + request + ", image=" + image + "]";
		}
	}
	
	public static final class OpenRouterTopProviderInfo {
		
		private Integer contextLength;
		
		private Integer maxCompletionTokens;
		
		private boolean moderated;
		
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
		
		@JsonProperty("is_moderated")
		public boolean isModerated() {
			return this.moderated;
		}
		
		public void setModerated(final boolean moderated) {
			this.moderated = moderated;
		}
		
		@Override
		public String toString() {
			return "OpenRouterTopProviderInfo [contextLength=" + contextLength + ", maxCompletionTokens=" + maxCompletionTokens + ", moderated=" + moderated + "]";
		}
	}
	
	public static final class OpenRouterPerRequestLimits {
		
		private Integer promptTokens;
		
		private Integer completionTokens;
		
		@JsonProperty("prompt_tokens")
		public Integer getPromptTokens() {
			return this.promptTokens;
		}
		
		public void setPromptTokens(final Integer promptTokens) {
			this.promptTokens = promptTokens;
		}
		
		@JsonProperty("completion_tokens")
		public Integer getCompletionTokens() {
			return this.completionTokens;
		}
		
		public void setCompletionTokens(final Integer completionTokens) {
			this.completionTokens = completionTokens;
		}
		
		@Override
		public String toString() {
			return "OpenRouterPerRequestLimits [promptTokens=" + promptTokens + ", completionTokens=" + completionTokens + "]";
		}
		
	}
	
	public static final class OpenRouterModelLinks {
		
		private String details;
		
		@JsonProperty("details")
		public String getDetails() {
			return this.details;
		}
		
		public void setDetails(final String details) {
			this.details = details;
		}
		
		@Override
		public String toString() {
			return "OpenRouterModelLinks [details=" + details + "]";
		}
		
	}
	
	@Override
	public String toString() {
		return "OpenRouterModelsResponse [data=" + data + "]";
	}
	
}
