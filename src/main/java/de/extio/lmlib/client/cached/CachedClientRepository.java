package de.extio.lmlib.client.cached;

public interface CachedClientRepository {
	
	CachedCompletion get(String key);
	
	void put(String key, CachedCompletion completion);
	
}
