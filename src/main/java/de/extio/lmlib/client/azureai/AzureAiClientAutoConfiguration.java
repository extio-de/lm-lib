package de.extio.lmlib.client.azureai;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import de.extio.lmlib.client.profile.ModelProfileService;
import de.extio.lmlib.client.token.Tokenizer;

@AutoConfiguration
@ConditionalOnClass(name = "com.azure.ai.inference.ChatCompletionsClient")
public class AzureAiClientAutoConfiguration {
	
	@Bean
	AzureAiClient azureAiClient(final ModelProfileService modelProfileService, final Tokenizer tokenizer) {
		return new AzureAiClient(modelProfileService, tokenizer);
	}
	
}
