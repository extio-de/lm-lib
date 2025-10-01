package de.extio.lmlib.client;

import java.util.function.Consumer;

import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfile.ModelProvider;

public interface Client {
	
	/**
	 * @deprecated use {@link #conversation(ModelCategory, Conversation)} instead. Conversation can be converted from a system and text by Conversation.create(system, text)
	 */
	@Deprecated
	Completion completion(ModelCategory modelCategory, String system, String text);
	
	Completion conversation(ModelCategory modelCategory, Conversation conversation);

	Completion streamConversation(ModelCategory modelCategory, Conversation conversation, Consumer<Chunk> chunkConsumer);
	
	ModelProvider getModelProvider();
}
