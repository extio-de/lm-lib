package de.extio.lmlib.client.azureai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import de.extio.lmlib.profile.ModelProfileService;
import de.extio.lmlib.token.Tokenizer;

@AutoConfiguration
@ConditionalOnClass(name = {"com.azure.ai.inference.ChatCompletionsClient" })
public class AzureAiClientAutoConfiguration {
	
	@Value("${extproxy.enabled:false}")
	private boolean proxyEnabled;
	
	@Value("${extproxy.host:}")
	private String proxyHost;
	
	@Value("${extproxy.port:3128}")
	private int proxyPort;
	
	@Value("${extproxy.username:}")
	private String proxyUser;
	
	@Value("${extproxy.password:}")
	private String proxyPassword;
	
	@Bean
	AzureAiClient azureAiClient(final ModelProfileService modelProfileService, final Tokenizer tokenizer) {
		return new AzureAiClient(modelProfileService, tokenizer, this.proxyEnabled, this.proxyHost, this.proxyPort, this.proxyUser, this.proxyPassword);
	}
	
}
