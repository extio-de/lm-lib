# lm-lib

The purpose of this library is to provide a comprehensive conversational AI system that enables efficient and effective human-computer interactions through natural language processing, conversation management, and text completion capabilities, utilizing caching, tokenization, and integration with external AI models and services to deliver a robust and scalable solution for applications such as chatbots, virtual assistants, and service platforms.

The program's key features include:

* Agent graph execution (incl. template engine and branching capabilities)
* Integration with external AI models and services (e.g., Azure AI and OpenAi Api compatible)
* Model profiles (choose the right model and parameters for the task)
* Conversation management and text completion with support for multiple prompt strategies and tokenization implementations
* Prompt caching

# Setup

To keep this library lean and compatible with different dependency trees, all dependencies must be provided by the application.

## Mandatory dependencies

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

## Optional dependencies

Some services benefit from Spring cache framework, e.g. the model profile service:

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>		

Don't forget to activate a CacheManager afterwards:

    @Bean
    public CacheManager cacheManager() {
        return new CaffeineCacheManager();
    }

## Model profiles

Models are managed with profiles. You can maintain multiple profiles for different models and parameters such as temperature.

These profiles must be mapped to fixed categories with the following application properties:

    profile.model.s=
    profile.model.m=
    profile.model.l=
    profile.model.hot=
    profile.model.cold=

The value is the name of the model profile .properties file, for example "llama3.3-70b-azure".

Agent and completion requests will be invoked with these categories and will load the corresponding model profile.

Profiles must be located in the classpath resources location (e.g. src/main/resources).

Examples can be found in test resources.

## OpenAi compatible client

Use tokenizer strategy jtokkit or llamaServer (if the model is hosted on a llama.cpp server)

Property:

    tokenizer.strategy=llamaServer

If you use jtokkit tokenizer:

    <dependency>
        <groupId>com.knuddels</groupId>
        <artifactId>jtokkit</artifactId>
    </dependency>

The library supports both chat completions and (legacy) text completions. Choose one of these options in your model profile:

    modelProvider=OAI_CHAT_COMPLETION
    modelProvider=OAI_TEXT_COMPLETION

Chat completions are recommended for most use cases and easy to use, since the library only needs to send the actual conversation to the server, whereas for text completions, the library needs to create a raw prompt that includes the correct instruction formatting (e.g. special tokens). Text completions are still preferred if you need more control over the raw prompt or if you don't use an instruct model. For text completions, it is required to select the right prompt template. See package .lmlib.prompt for available prompt templates or implement your own PromptStrategy (must be a bean in the spring context). There is also the special NoPromptStrategy ("no") that does not add any special tokens.
To activate a prompt template, set the following property in your model profile (corresponds to getPromptName()):

    prompts=llama3

Example profile: src/test/resources/xxx-local.properties

## Azure AI client

Use tokenizer strategy jtokkit.

Property:

    tokenizer.strategy=jtokkit

Example profile: src/test/resources/llama3.3-70b-azure.properties

    <dependency>
    	<groupId>com.azure</groupId>
    	<artifactId>azure-ai-inference</artifactId>
    </dependency>
    <dependency>
    	<groupId>com.squareup.okhttp3</groupId>
    	<artifactId>okhttp</artifactId>
    </dependency>    
    <dependency>
    	<groupId>com.knuddels</groupId>
    	<artifactId>jtokkit</artifactId>
    </dependency>
    <dependency>
    	<groupId>com.github.ben-manes.caffeine</groupId>
    	<artifactId>caffeine</artifactId>
    </dependency>

# Usage

1. Setup dependencies, see above
2. Configure model profiles, see above
3. Implement a CachedClientRepository if you need your requests cached
4. For chats or completions, use ClientService service bean
5. For agentic flows, create an AgentContext and use AgentExecutorService service bean

# Usage Examples

Client service: clientService.getClient(ModelCategory.MEDIUM);
Client usage details: CompletionClientTest and AzureAiClientTest
Agentic flow: AgentTest#agenticFlow and AgentTest#streamedAgenticFlow

## Completions and Streaming

The library supports both streaming and non-streaming completions.

Streaming methods accept a Consumer to receive the next chunk (direct Client usage) or a callback on the next AgentContext update (Agents). They also return a full Completion object or the final AgentContext at the end of the execution that also contains the statistics.

Non-streaming methods only return the final objects at the end of the execution. This approach is much more resource-friendly and therefore recommended if you don't need to display or process the results in real-time.

# Completions Cache

All language model requests will be cached if you add a CachedClientRepository bean to your context.

# Completion Interceptors

You can intercept every Conversation and Completion by implementing CompletionInterceptor.
This is for example useful for accounting purposes or to implement a circuit breaker.
The client will autowire all implementations and intercept requests accordingly.

# Grader

The library includes a grader component that evaluates the correctness of agent responses.

Example:

    assertTrue(Grader.assessScoreBinary("Does the following blog post mention the feature " + feature, resultContext.getStringValue("blogPost"), this.clientService));

# Agentic Flows

This library contains a graph executor for implementing comprehensive processes using agent-based design patterns. 

You can implement your Agents either by creating an Agent record or by extending BaseAgent - choose the approach that best fits your needs.
The agentic flow is started with an AgentContext and an initial agent.
Example:

    final var context = new AgentContext(List.of(new Agent1(), new Agent2()));
    context.getContext().setStringValue("key", "Payload here");
    context.getContext().setStringValues("key2", List.of("All context keys", "support collections", "for aggregation purposes"));
    context.getContext().setValue("key3", new RandomDatatype());
    final var resultContexts = agentExecutorService.walk("Agent1", context)
    final var resultContext = resultContexts.getFirst(); // You may receive multiple contexts if your graph branches out
    LOGGER.info(resultContext.getStringValue("blogPost") + "\n\n" + resultContext.getGraph().toString() + "\n" + resultContext.getRequestStatistic().toString());

Streaming is also supported.

See class AgentTest for examples.

# Agent implementations



# Announcement generated by AgentTest#agenticFlow

## Introducing Advanced Agent Orchestration: Dynamic Conversations & Parallel Processing

We're excited to announce significant enhancements to our agent framework, designed to empower developers building sophisticated conversational AI applications. This release introduces a powerful new system for orchestrating multiple agents, enabling more dynamic, efficient, and complex interactions.

Here's a breakdown of what's new:

**1. LLM Conversation Orchestration:**

Our framework now provides a robust system for chaining multiple agents together. Each agent can perform a specific task, and the framework intelligently manages the flow of information between them, creating a pipeline for complex conversational workflows.

**2. Dynamic Prompt Templating with Branching:**

Say goodbye to rigid prompts!  Our new templating engine allows for dynamic prompt generation based on the context.  Critically, it now supports *branching*. This means a single input can trigger multiple prompt variations, allowing agents to explore different avenues of conversation or processing paths based on data within the input.

**3. Asynchronous Task Execution for Parallel Processing:**

Speed and efficiency are key. We've implemented asynchronous task execution using `CompletableFuture` and an `ExecutorService`. This allows agents to process multiple inputs (splits) *in parallel*, significantly reducing overall processing time and improving responsiveness.

**4. Context Merging Across Multiple Agent Splits:**

When branching occurs, the framework intelligently merges the results from different execution paths. This ensures a unified context is maintained, allowing subsequent agents to access a complete and consolidated view of the conversation or task.

**5. Agent Chaining and Next Agent Selection:**

The framework dynamically determines the next agent to execute based on the current context. This allows for creating complex, stateful conversations where the flow adapts based on user input and agent responses.  The system can also signal the end of the chain, indicating completion.

**What does this mean for you?**

These improvements unlock a new level of flexibility and power for building conversational AI applications. You can now:

*   Create more complex and nuanced conversational flows.
*   Handle a wider range of user inputs and scenarios.
*   Improve the performance and responsiveness of your agents.
*   Build more scalable and maintainable AI systems.

We're confident that these advancements will significantly enhance your ability to build intelligent and engaging conversational experiences. Stay tuned for more updates and documentation!

*gemma3-12b-it-q5_k_m*
*M, CodeSummarizer, ↴, M, FeatureIdentifier, →, ⇉ 1/5, M, FeatureAnalyzer, 5↣1, →, M, AnswerGenerator*
*AgentRequestStatistic [requests=8, cachedPrompts=0, inTokens=27393, outTokens=4861, requestDuration=PT3M30.415881394S, getEffectiveDuration()=PT2M22.102597491S, getTps()=226.98, getOutTps()=34.21, getCost()=0]*
