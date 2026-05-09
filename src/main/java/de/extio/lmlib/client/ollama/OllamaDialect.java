package de.extio.lmlib.client.ollama;

import com.fasterxml.jackson.annotation.JsonValue;

import de.extio.lmlib.profile.ModelProfile;

public interface OllamaDialect {

	default ThinkMode think(final ModelProfile modelProfile) {
		return ThinkMode.ENABLED;
	}

	enum ThinkMode {
		DISABLED(Boolean.FALSE),
		ENABLED(Boolean.TRUE),
		LOW("low"),
		MEDIUM("medium"),
		HIGH("high");

		private final Object wireValue;

		ThinkMode(final Object wireValue) {
			this.wireValue = wireValue;
		}

		@JsonValue
		public Object getWireValue() {
			return this.wireValue;
		}
	}
}