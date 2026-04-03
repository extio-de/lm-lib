package de.extio.lmlib.client;

import java.util.List;
import java.util.function.Consumer;

import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;

public interface Client {
	
	Completion conversation(ModelCategory modelCategory, Conversation conversation, ToolCallData toolCallData, boolean skipCache);

	Completion conversation(ModelProfile modelProfile, Conversation conversation, ToolCallData toolCallData, boolean skipCache);

	Completion streamConversation(ModelCategory modelCategory, Conversation conversation, Consumer<Chunk> chunkConsumer, ToolCallData toolCallData, boolean skipCache);
	
	Completion streamConversation(ModelProfile modelProfile, Conversation conversation, Consumer<Chunk> chunkConsumer, ToolCallData toolCallData, boolean skipCache);

	List<String> getModelNames(ModelProfile modelProfile, boolean forceReload);
	
	ModelProvider getModelProvider();

	boolean supportsToolCalling();
}
