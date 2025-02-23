package de.extio.lmlib.token;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration
public class TokenizerAutoConfiguration {
	
	@Bean
	@ConditionalOnProperty(name = "tokenizer.strategy", havingValue = "llamaServer")
	@ConditionalOnMissingBean(type = "de.extio.lmlib.token.Tokenizer")
	Tokenizer llamaServerTokenizer(@Qualifier("lmLibWebClientBuilder") final WebClient.Builder webClientBuilder) {
		return new LlamaServerTokenizer(webClientBuilder);
	}
	
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(name = "com.knuddels.jtokkit.Encodings")
	@ConditionalOnProperty(name = "tokenizer.strategy", havingValue = "jTokkit")
	@ConditionalOnMissingBean(type = "de.extio.lmlib.token.Tokenizer")
	public static class SomeServiceConfiguration {
		
		@Bean
		Tokenizer jTokkitTokenizer() {
			return new JTokkitTokenizer();
		}
		
	}
	
}
