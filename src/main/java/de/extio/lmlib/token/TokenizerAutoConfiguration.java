package de.extio.lmlib.token;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnProperty(prefix = "lmlib.tokenizer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TokenizerAutoConfiguration {
	
	@Bean
	@ConditionalOnBean(name = "lmLibRestClientBuilder")
	@ConditionalOnMissingBean(LlamaServerTokenizer.class)
	Tokenizer llamaServerTokenizer(@Qualifier("lmLibRestClientBuilder") final RestClient.Builder restClientBuilder) {
		return new LlamaServerTokenizer(restClientBuilder);
	}
	
	@Bean
	@ConditionalOnClass(name = "com.knuddels.jtokkit.Encodings")
	@ConditionalOnMissingBean(JTokkitTokenizer.class)
	Tokenizer jTokkitTokenizer() {
		return new JTokkitTokenizer();
	}
	
	@Bean
	@ConditionalOnMissingBean(FallbackTokenizer.class)
	Tokenizer fallbackTokenizer() {
		return new FallbackTokenizer();
	}
	
	@Bean
	@ConditionalOnMissingBean
	TokenizerResolver tokenizerResolver(final java.util.List<Tokenizer> tokenizers) {
		return new TokenizerResolver(tokenizers);
	}

}
