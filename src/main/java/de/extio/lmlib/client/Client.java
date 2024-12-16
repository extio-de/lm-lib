package de.extio.lmlib.client;

import de.extio.lmlib.client.profile.ModelCategory;
import de.extio.lmlib.client.profile.ModelProfile.ModelProvider;

public interface Client {
	
	Completion completion(ModelCategory modelCategory, String system, String question, String fullText);
	
	Completion conversation(ModelCategory modelCategory, Conversation conversation);
	
	ModelProvider getModelProvider();
}
