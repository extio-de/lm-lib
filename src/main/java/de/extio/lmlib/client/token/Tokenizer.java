package de.extio.lmlib.client.token;

import java.util.List;

import de.extio.lmlib.client.profile.ModelProfile;

public interface Tokenizer {
	
	List<Long> tokenize(String txt, ModelProfile modelProfile);
	
	int count(String txt, ModelProfile modelProfile);
	
	String detokenize(List<Long> tokens, ModelProfile modelProfile);
	
}
