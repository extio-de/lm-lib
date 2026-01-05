package de.extio.lmlib.prompt;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "lmlib.prompt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PromptAutoConfiguration {
	
	@Bean
	@ConditionalOnMissingBean
	PromptStrategyFactory promptStrategyFactory(final List<PromptStrategy> strategies) {
		return new PromptStrategyFactory(strategies);
	}
	
	@Bean
	@ConditionalOnMissingBean
	AlpacaShortPromptStrategy alpacaShortPromptStrategy() {
		return new AlpacaShortPromptStrategy();
	}
	
	@Bean
	@ConditionalOnMissingBean
	ChatMLPromptStrategy chatMLPromptStrategy() {
		return new ChatMLPromptStrategy();
	}
	
	@Bean
	@ConditionalOnMissingBean
	GemmaPromptStrategy gemmaPromptStrategy() {
		return new GemmaPromptStrategy();
	}
	
	@Bean
	@ConditionalOnMissingBean
	GptOssPromptStrategy gptOssPromptStrategy() {
		return new GptOssPromptStrategy();
	}
	
	@Bean
	@ConditionalOnMissingBean
	Llama3PromptStrategy llama3PromptStrategy() {
		return new Llama3PromptStrategy();
	}
	
	@Bean
	@ConditionalOnMissingBean
	Llama4PromptStrategy llama4PromptStrategy() {
		return new Llama4PromptStrategy();
	}
	
	@Bean
	@ConditionalOnMissingBean
	MistralNemoPromptStrategy mistralNemoPromptStrategy() {
		return new MistralNemoPromptStrategy();
	}
	
	@Bean
	@ConditionalOnMissingBean
	MistralPromptStrategy mistralPromptStrategy() {
		return new MistralPromptStrategy();
	}
	
	@Bean
	@ConditionalOnMissingBean
	NoPromptStrategy noPromptStrategy() {
		return new NoPromptStrategy();
	}
	
	@Bean
	@ConditionalOnMissingBean
	Phi3PromptStrategy phi3PromptStrategy() {
		return new Phi3PromptStrategy();
	}
	
	@Bean
	@ConditionalOnMissingBean
	Phi4PromptStrategy phi4PromptStrategy() {
		return new Phi4PromptStrategy();
	}
	
	@Bean
	@ConditionalOnMissingBean
	Qwen3PromptStrategy qwen3PromptStrategy() {
		return new Qwen3PromptStrategy();
	}
	
	@Bean
	@ConditionalOnMissingBean
	VicunaPromptStrategy vicunaPromptStrategy() {
		return new VicunaPromptStrategy();
	}
	
}
