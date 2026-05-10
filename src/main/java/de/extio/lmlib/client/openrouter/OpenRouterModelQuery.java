package de.extio.lmlib.client.openrouter;

import java.util.List;

public record OpenRouterModelQuery(boolean userFiltered, String category, List<String> supportedParameters, List<String> outputModalities) {

	public OpenRouterModelQuery {
		supportedParameters = supportedParameters == null ? List.of() : List.copyOf(supportedParameters);
		outputModalities = outputModalities == null ? List.of() : List.copyOf(outputModalities);
	}

	public boolean hasServerSideFilters() {
		return this.category != null && !this.category.isBlank() || !this.supportedParameters.isEmpty() || !this.outputModalities.isEmpty();
	}

}