package de.extio.lmlib.client.profile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.stereotype.Service;

import de.extio.lmlib.client.profile.ModelProfile.ModelProvider;

@Service
public class ModelProfileService {
	
	private final Environment environment;
	
	private static final String MODEL_PROFILE_PREFIX = "profile";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ModelProfileService.class);
	
	public ModelProfileService(final Environment environment) {
		this.environment = environment;
	}
	
	@Cacheable("modelProfiles")
	public ModelProfile getModelProfile(final String modelProfile) {
		final var modelName = this.environment.getProperty(modelProfile);
		if (modelName == null) {
			throw new IllegalStateException("No model found for profile: " + modelProfile);
		}
		try {
			final var resource = new ResourcePropertySource("classpath:" + modelName + ".properties");
			final var promptTemplate = Optional.ofNullable(resource.getProperty("prompts"))
					.map(Object::toString)
					.orElseThrow(() -> new IllegalStateException("No model name found for model: " + modelName));
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
			final var maxContinuations = Optional.ofNullable(resource.getProperty("maxContinuations"))
					.map(Object::toString)
					.map(Integer::parseInt)
					.orElseThrow(() -> new IllegalStateException("No model maxContinuations found for model: " + modelName));
			final var modelProvider = Optional.ofNullable(resource.getProperty("modelProvider"))
					.map(Object::toString)
					.map(ModelProvider::valueOf)
					.orElseThrow(() -> new IllegalStateException("No model modelProvider found for model: " + modelName));
			final var modelNameCfg = Optional.ofNullable(resource.getProperty("modelName"))
					.map(Object::toString)
					.orElseThrow(() -> new IllegalStateException("No model modelName found for model: " + modelName));
			final var url = Optional.ofNullable(resource.getProperty("url"))
					.map(Object::toString)
					.orElseThrow(() -> new IllegalStateException("No model url found for model: " + modelName));
			final var apiKey = Optional.ofNullable(resource.getProperty("apiKey"))
					.map(Object::toString)
					.orElseThrow(() -> new IllegalStateException("No model apiKey found for model: " + modelName));
			
			return new ModelProfile(promptTemplate, tokenEncoding, maxTokens, maxContextLength, temperature, topP, maxContinuations, modelProvider, modelNameCfg, url, apiKey);
		}
		catch (final IOException e) {
			LOGGER.error("Error while reading model profile", e);
			return null;
		}
	}
	
	public List<String> getModelProfileUrls(final ModelProvider modelProvider) {
		final var modelProfiles = Binder.get(this.environment)
				.bind(MODEL_PROFILE_PREFIX, Bindable.mapOf(String.class, String.class))
				.orElse(Collections.emptyMap());
		
		return modelProfiles.values().stream()
				.filter(model -> model != null && !model.isEmpty())
				.map(model -> {
					try {
						final var resource = new ResourcePropertySource("classpath:" + model + ".properties");
						if (modelProvider.name().equals(resource.getProperty("modelProvider"))) {
							return resource.getProperty("url") != null ? resource.getProperty("url").toString() : null;
						}
						return null;
					}
					catch (final IOException e) {
						LOGGER.error("Error while reading model profile for: " + model, e);
						return null;
					}
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}
}
