package de.extio.lmlib.client.oai.completion;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponse(@JsonProperty("error") ErrorMessage error) {

	public record ErrorMessage(@JsonProperty("message") String message) {

	}
}
