package de.extio.lmlib.agent;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import de.extio.lmlib.agent.responsehandler.AgentResponseHandler;
import de.extio.lmlib.profile.ModelCategory;

public record Agent(String name,
        AgentType agentType,
        ModelCategory modelCategory,
        String systemPrompt,
        String textTemplate,
        AgentResponseHandler responseHandler,
        Consumer<AgentContext> preProcessor,
        Consumer<AgentContext> postProcessor,
        Function<List<AgentContext>,
        List<AgentContext>> merger,
        Function<AgentContext, AgentNext> chooseNext) implements BaseAgent {
    
    public Agent {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }
        if (agentType == null) {
            throw new IllegalArgumentException("Agent type cannot be null");
        }
    }
    
    @Override
    public void preProcess(final AgentContext context) {
        if (preProcessor != null) {
            preProcessor.accept(context);
        }
        BaseAgent.super.preProcess(context);
    }
    
    @Override
    public void postProcess(final AgentContext context) {
        if (postProcessor != null) {
            postProcessor.accept(context);
        }
        BaseAgent.super.postProcess(context);
    }
    
    @Override
    public List<AgentContext> merge(final List<AgentContext> contexts) {
        if (merger != null) {
            return merger.apply(contexts);
        }
        return BaseAgent.super.merge(contexts);
    }
    
    @Override
    public AgentNext chooseNextAgent(final AgentContext context) {
        if (chooseNext != null) {
            return chooseNext.apply(context);
        }
        return BaseAgent.super.chooseNextAgent(context);
    }
    
}
