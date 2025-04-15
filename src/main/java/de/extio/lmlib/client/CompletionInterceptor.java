package de.extio.lmlib.client;

import de.extio.lmlib.profile.ModelCategory;

public interface CompletionInterceptor {
	
	Conversation before(ModelCategory modelCategory, Conversation conversation);
	
	Completion after(ModelCategory modelCategory, Completion completion);
	
}
