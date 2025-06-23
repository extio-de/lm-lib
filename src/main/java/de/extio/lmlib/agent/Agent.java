package de.extio.lmlib.agent;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import de.extio.lmlib.profile.ModelCategory;

public record Agent(String name, AgentType agentType, ModelCategory modelCategory, String systemPrompt, String textTemplate, AgentResponseHandler responseHandler, Consumer<AgentContext> preProcessor, Consumer<AgentContext> postProcessor, Function<List<AgentContext>, List<AgentContext>> merger, Function<AgentContext, AgentNext> chooseNext) implements BaseAgent {
	
}
