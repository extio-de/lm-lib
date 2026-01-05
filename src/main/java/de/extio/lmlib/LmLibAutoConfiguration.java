package de.extio.lmlib;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import de.extio.lmlib.agent.AgentExecutorService;
import de.extio.lmlib.client.ClientService;
import de.extio.lmlib.client.oai.ModelNameSupplier;
import de.extio.lmlib.client.oai.completion.chat.ChatCompletionClient;
import de.extio.lmlib.client.oai.completion.text.TextCompletionClient;
import de.extio.lmlib.profile.ModelProfileService;
import de.extio.lmlib.prompt.AlpacaShortPromptStrategy;
import de.extio.lmlib.prompt.ChatMLPromptStrategy;
import de.extio.lmlib.prompt.GemmaPromptStrategy;
import de.extio.lmlib.prompt.GptOssPromptStrategy;
import de.extio.lmlib.prompt.Llama3PromptStrategy;
import de.extio.lmlib.prompt.Llama4PromptStrategy;
import de.extio.lmlib.prompt.MistralNemoPromptStrategy;
import de.extio.lmlib.prompt.MistralPromptStrategy;
import de.extio.lmlib.prompt.NoPromptStrategy;
import de.extio.lmlib.prompt.Phi3PromptStrategy;
import de.extio.lmlib.prompt.Phi4PromptStrategy;
import de.extio.lmlib.prompt.PromptStrategy;
import de.extio.lmlib.prompt.PromptStrategyFactory;
import de.extio.lmlib.prompt.Qwen3PromptStrategy;
import de.extio.lmlib.prompt.VicunaPromptStrategy;

@AutoConfiguration
public class LmLibAutoConfiguration {
	
	@Bean
	@ConditionalOnMissingBean
	ModelProfileService modelProfileService(final Environment environment) {
		return new ModelProfileService(environment);
	}
	
	@Bean
	@ConditionalOnMissingBean
	ClientService clientService() {
		return new ClientService();
	}
	
	@Bean
	@ConditionalOnMissingBean
	AgentExecutorService agentExecutorService() {
		return new AgentExecutorService();
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
