package de.extio.lmlib.token;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnProperty(prefix = "lmlib.tokenizer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TokenizerAutoConfiguration {
	
	@Bean
	@ConditionalOnProperty(name = "tokenizer.strategy", havingValue = "llamaServer")
	@ConditionalOnMissingBean(type = "de.extio.lmlib.token.Tokenizer")
	Tokenizer llamaServerTokenizer(@Qualifier("lmLibRestClientBuilder") final RestClient.Builder restClientBuilder) {
		return new LlamaServerTokenizer(restClientBuilder);
	}
	
	@Bean
	@ConditionalOnClass(name = "com.knuddels.jtokkit.Encodings")
	@ConditionalOnProperty(name = "tokenizer.strategy", havingValue = "jTokkit", matchIfMissing = true)
	@ConditionalOnMissingBean(type = "de.extio.lmlib.token.Tokenizer")
	Tokenizer jTokkitTokenizer() {
		return new JTokkitTokenizer();
	}

}
