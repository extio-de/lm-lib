package de.extio.lmlib.client;

import de.extio.lmlib.profile.ModelProfile;

public interface CompletionInterceptor {
	
	Conversation before(ModelProfile modelProfile, Conversation conversation);
	
	Completion after(ModelProfile modelProfile, Conversation conversation, Completion completion);
	
}
