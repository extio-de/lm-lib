package de.extio.lmlib.profile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.stereotype.Service;

import de.extio.lmlib.profile.ModelProfile.ModelProvider;

@Service
public class ModelProfileService {
	
	private final Environment environment;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ModelProfileService.class);
	
	public ModelProfileService(final Environment environment) {
		this.environment = environment;
	}
	
	public ModelProfile getModelProfile(final String modelProfile) {
		return this.getModelProfile(modelProfile, null);
	}
	
	@Cacheable("modelProfiles")
	public ModelProfile getModelProfile(final String modelProfile, final ModelCategory modelCategory) {
		final var modelName = this.environment.getProperty(modelProfile);
		if (modelName == null) {
			throw new IllegalStateException("No model found for profile: " + modelProfile);
		}
		try {
			
			final var resource = new ResourcePropertySource("classpath:" + modelName + ".properties");
			
			final var promptTemplate = Optional.ofNullable(resource.getProperty("prompts"))
					.map(Object::toString)
					.orElse("");
			final var tokenEncoding = Optional.ofNullable(resource.getProperty("tokenEncoding"))
					.map(Object::toString)
					.orElse("none");
			final var maxTokens = Optional.ofNullable(resource.getProperty("maxTokens"))
					.map(Object::toString)
					.map(Integer::parseInt)
					.orElseThrow(() -> new IllegalStateException("No model maxTokens found for model: " + modelName));
			final var maxContextLength = Optional.ofNullable(resource.getProperty("maxContextLength"))
					.map(Object::toString)
					.map(Integer::parseInt)
					.orElseThrow(() -> new IllegalStateException("No model maxContextLength found for model: " + modelName));
			final var temperature = Optional.ofNullable(resource.getProperty("temperature"))
					.map(Object::toString)
					.map(Double::parseDouble)
					.orElseThrow(() -> new IllegalStateException("No model temperature found for model: " + modelName));
			final var topP = Optional.ofNullable(resource.getProperty("topP"))
					.map(Object::toString)
					.map(Double::parseDouble)
					.orElseThrow(() -> new IllegalStateException("No model topP found for model: " + modelName));
			final var modelProvider = Optional.ofNullable(resource.getProperty("modelProvider"))
					.map(Object::toString)
					.map(ModelProvider::valueOf)
					.orElseThrow(() -> new IllegalStateException("No model modelProvider found for model: " + modelName));
			final var modelNameCfg = Optional.ofNullable(resource.getProperty("modelName"))
					.map(Object::toString)
					.orElse(null);
			final var url = Optional.ofNullable(resource.getProperty("url"))
					.map(Object::toString)
					.orElseThrow(() -> new IllegalStateException("No model url found for model: " + modelName));
			final var costInToken = Optional.ofNullable(resource.getProperty("cost1MInTokens"))
					.map(Object::toString)
					.map(value -> new BigDecimal(value))
					.orElse(BigDecimal.ZERO)
					.divide(new BigDecimal(1000000.0));
			final var costOutToken = Optional.ofNullable(resource.getProperty("cost1MOutTokens"))
					.map(Object::toString)
					.map(value -> new BigDecimal(value))
					.orElse(BigDecimal.ZERO)
					.divide(new BigDecimal(1000000.0));
			final var reasoningEffort = Optional.ofNullable(resource.getProperty("reasoningEffort"))
					.map(Object::toString)
					.orElse(null);
			final var reasoningSummaryDetails = Optional.ofNullable(resource.getProperty("reasoningSummaryDetails"))
					.map(Object::toString)
					.orElse(null);
			final var category = Optional
					.ofNullable(modelCategory)
					.map(ModelCategory::getShortName)
					.orElseGet(() -> (String)resource.getProperty("category"));
			
			//To resolve api key placeholder ${apikey} in model-name.properties file
			String apiKey = null;
			synchronized (this.environment) {
				if (this.environment instanceof final ConfigurableEnvironment configurableEnvironment) {
					configurableEnvironment.getPropertySources().addFirst(resource);
				}
				apiKey = Optional.ofNullable(this.environment.getProperty("apiKey"))
						.map(Object::toString)
						.orElse(null);
			}
			
			return new ModelProfile(promptTemplate, tokenEncoding, maxTokens, maxContextLength, temperature, topP, modelProvider, modelNameCfg, url, apiKey, costInToken, costOutToken, reasoningEffort, reasoningSummaryDetails, category);
		}
		catch (final IOException e) {
			LOGGER.error("Error while reading model profile", e);
			return null;
		}
	}
	
	public ModelProfile getModelProfile(final ModelCategory modelCategory) {
		return this.getModelProfile(modelCategory.getModelProfile(), modelCategory);
	}
}
