package de.extio.lmlib.client.oai.responses;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import de.extio.lmlib.profile.ModelProfile;

@TestConfiguration
public class ResponsesApiDialectTestConfiguration {

	@Bean
	MutableOpenAiResponsesApiDialect openAiResponsesApiDialect() {
		return new MutableOpenAiResponsesApiDialect();
	}

	public static class MutableOpenAiResponsesApiDialect implements OpenAiResponsesApiDialect {

		private volatile ReasoningEffort reasoning = ReasoningEffort.MEDIUM;

		@Override
		public ResponsesReasoning reasoning(final ModelProfile modelProfile) {
			return this.reasoning == null ? null : new ResponsesReasoning(this.reasoning, null, null);
		}

		public void setReasoning(final ReasoningEffort reasoning) {
			this.reasoning = reasoning;
		}
	}
}