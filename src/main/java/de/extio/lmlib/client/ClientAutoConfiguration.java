package de.extio.lmlib.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import de.extio.lmlib.client.oai.ModelNameSupplier;
import de.extio.lmlib.client.oai.completion.chat.ChatCompletionClient;
import de.extio.lmlib.client.oai.completion.text.TextCompletionClient;
import de.extio.lmlib.profile.ProfileAutoConfiguration;
import de.extio.lmlib.token.TokenizerAutoConfiguration;

@AutoConfiguration
@AutoConfigureAfter({ProfileAutoConfiguration.class, TokenizerAutoConfiguration.class, RestClientConfiguration.class})
@ConditionalOnProperty(prefix = "lmlib.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ClientAutoConfiguration {
	
	@Bean
	@ConditionalOnMissingBean
	ClientService clientService() {
		return new ClientService();
	}
	
	@Bean
	@ConditionalOnMissingBean
	ModelNameSupplier modelNameSupplier() {
		return new ModelNameSupplier();
	}
	
	@Bean
	@ConditionalOnMissingBean
	TextCompletionClient textCompletionClient() {
		return new TextCompletionClient();
	}
	
	@Bean
	@ConditionalOnMissingBean
	ChatCompletionClient chatCompletionClient() {
		return new ChatCompletionClient();
	}
	
}
