package de.extio.lmlib.agent;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import de.extio.lmlib.client.ClientAutoConfiguration;

@AutoConfiguration
@AutoConfigureAfter(ClientAutoConfiguration.class)
@ConditionalOnProperty(prefix = "lmlib.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentAutoConfiguration {
	
	@Bean
	@ConditionalOnMissingBean
	AgentExecutorService agentExecutorService() {
		return new AgentExecutorService();
	}
	
}
