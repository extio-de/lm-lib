package de.extio.lmlib.client.cached;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
class TestCachedClientRepository implements CachedClientRepository {
	
	private final ConcurrentMap<String, CachedCompletion> cache = new ConcurrentHashMap<>();
	
	@Configuration
	static class Config {
		
		@Bean
		CachedClientRepository cachedClientRepository() {
			return new TestCachedClientRepository();
		}

	}

	@Override
	public CachedCompletion get(final String key) {
		return this.cache.get(key);
	}
	
	@Override
	public void put(final String key, final CachedCompletion completion) {
		this.cache.put(key, completion);
	}
	
	void reset() {
		this.cache.clear();
	}
	
}
