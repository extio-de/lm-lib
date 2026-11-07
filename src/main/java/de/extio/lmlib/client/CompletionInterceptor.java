package de.extio.lmlib.client;

import de.extio.lmlib.profile.ModelProfile;

public interface CompletionInterceptor {
	
	Conversation before(ModelProfile modelProfile, Conversation conversation, ToolCallData toolCallData);
	
	Completion after(ModelProfile modelProfile, Conversation conversation, ToolCallData toolCallData, Completion completion);
	
}
