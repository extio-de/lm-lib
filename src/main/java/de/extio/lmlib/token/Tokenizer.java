package de.extio.lmlib.token;

import java.util.List;

import de.extio.lmlib.profile.ModelProfile;

public interface Tokenizer {
	
	String getName();
	
	List<Long> tokenize(String txt, ModelProfile modelProfile);
	
	int count(String txt, ModelProfile modelProfile);
	
	String detokenize(List<Long> tokens, ModelProfile modelProfile);
	
}
