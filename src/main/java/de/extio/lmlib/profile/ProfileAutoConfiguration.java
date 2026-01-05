package de.extio.lmlib.profile;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnProperty(prefix = "lmlib.profile", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProfileAutoConfiguration {
	
	@Bean
	@ConditionalOnMissingBean
	ModelProfileService modelProfileService(final Environment environment) {
		return new ModelProfileService(environment);
	}
	
}
