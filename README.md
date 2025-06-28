# lm-lib

The purpose of lm-lib is to provide a modular, extensible Java framework for interacting with large language models (LLMs) from various providers (such as OpenAI, Azure AI, and local models). It abstracts away the complexities of prompt construction, conversation management, and model configuration, enabling developers to easily switch between models and providers. The library supports advanced features like streaming completions, request caching, statistics tracking, and agentic flow execution—allowing for the orchestration of multi-step, branching, and parallel conversational workflows. Integration with Spring ensures flexible configuration and dependency management, making it suitable for building scalable, maintainable, and sophisticated LLM-powered applications.

The libraries's key features include:

- Unified API for multiple LLM providers (OpenAI, Azure, local models, etc.)  
- Streaming and non-streaming completions  
- Agentic flow execution
- Model profile configuration and switching  
- Prompt and conversation management utilities  
- Request caching and statistics tracking  
- Pluggable interceptors, e.g. for rate limiting
- Modular architecture for easy extension and customization  
- Spring integration for dependency injection and configuration  

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

- Client service: clientService.getClient(ModelCategory.MEDIUM);
- Client usage details: CompletionClientTest and AzureAiClientTest
- Agentic flow: AgentTest#agenticFlow and AgentTest#streamedAgenticFlow

# Completions and Streaming

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

## Documentation for `BaseAgent` Interface Methods

This document details the public methods of the `BaseAgent` interface, providing developers with information on their purpose and usage within agentic LLM flows.  These methods allow customization of agent behavior, pre/post processing of contexts, merging of results, and choosing the next agent in a flow.

**1. `String name()`**

*   **Purpose:** Returns a unique string identifier for this agent. This name is used to reference the agent within an agent flow and for logging/debugging purposes.
*   **Usage:** Implementations *must* override this method to return a meaningful name.  This name should be consistent across all instances of the same agent type.
*   **Returns:** A `String` representing the agent's name.
*   **Throws:** `UnsupportedOperationException` if not implemented.

**2. `AgentType agentType()`**

*   **Purpose:** Defines the core functionality or role of this agent within the overall flow. The `AgentType` enum (not shown in provided code but implied) likely defines categories like "completion", "processing", "start conversation" etc., influencing how the agent interacts with the LLM client and other agents.
*   **Usage:** Implementations *must* override this method to return the appropriate `AgentType`. This is crucial for controlling the execution logic within the `execute()` method.
*   **Returns:** An `AgentType` enum value.
*   **Throws:** `UnsupportedOperationException` if not implemented.

**3. `ModelCategory modelCategory()`**

*   **Purpose:** Specifies the desired size/capability category of the language model to use when interacting with the LLM client.  This allows for cost optimization or performance tuning by selecting models appropriate for the task.
*   **Usage:** Implementations can optionally override this method to customize the model category. The default implementation returns `ModelCategory.MEDIUM`.
*   **Returns:** A `ModelCategory` enum value.
*   **Default:** `ModelCategory.MEDIUM`

**4. `String systemPrompt()`**

*   **Purpose:** Provides a system-level instruction that sets the context and behavior for the LLM. This prompt is prepended to user input when creating a conversation.
*   **Usage:** Implementations can optionally override this method to provide a custom system prompt tailored to their specific task. If no prompt is needed, it should return `null`.
*   **Returns:** A `String` containing the system prompt.
*   **Default:** `null`

**5. `String textTemplate()`**

*   **Purpose:** Defines a template string for generating input text for the LLM.  The template can include placeholders (e.g., `{{key}}`) which are dynamically replaced with values from the `AgentContext`. `[[key]]` style placeholders are a convenient way to branch out / parallelize execution flows. See also the section about text template placeholders below.
*   **Usage:** Implementations can optionally override this method to define a template. This is useful for constructing prompts based on data in the agent context.
*   **Returns:** A `String` containing the text template.
*   **Default:** `null`

**6. `AgentResponseHandler responseHandler()`**

*   **Purpose:**  Provides a mechanism for processing the raw LLM response. Different handlers can parse responses into structured data, extract specific information, or perform validation.
*   **Usage:** Implementations can optionally override this method to return a custom `AgentResponseHandler`. The default implementation returns a `TextAgentResponseHandler` configured to handle responses under the "response" key.
*   **Returns:** An `AgentResponseHandler` instance.
*   **Default:** `new TextAgentResponseHandler("response")`

**7. `void preProcess(final AgentContext context)`**

*   **Purpose:** Allows performing actions *before* the main logic of the agent is executed. This could involve modifying the `AgentContext`, preparing data, or setting up necessary resources.
*   **Usage:** Implementations can optionally override this method to add pre-processing steps.
*   **Parameters:** `context`: The `AgentContext` object representing the current state of the agent flow.

**8. `void postProcess(final AgentContext context)`**

*   **Purpose:** Allows performing actions *after* the main logic of the agent is executed. This could involve cleaning up resources, updating the `AgentContext`, or logging results.
*   **Usage:** Implementations can optionally override this method to add post-processing steps.
*   **Parameters:** `context`: The `AgentContext` object representing the current state of the agent flow.

**9. `List<AgentContext> merge(final List<AgentContext> contexts)`**

*   **Purpose:** Combines multiple `AgentContext` objects into a collection of potentially new `AgentContext` objects. This method provides flexible context aggregation, supporting scenarios beyond simply reducing many contexts to one. It allows for one-to-one, one-to-many, many-to-one, and many-to-many merging strategies. The merge() step is executed after all branch executions (depth 1) and their postProcess() steps have been completed.
*   **Usage:** Implementations can optionally override this method to define a custom merging strategy tailored to their specific needs. The default implementation returns `null`, indicating no merging is performed. A non-null return value signifies the merged contexts.
*   **Parameters:** `contexts`: A list of `AgentContext` objects to merge.
*   **Returns:** A `List<AgentContext>` containing the resulting merged contexts. The size of the returned list can vary depending on the implemented merging logic:
    *   **One-to-One:** If each input context results in exactly one output context, the returned list will have the same size as the input list.
    *   **Many-to-One:**  If multiple input contexts are combined into a single output context, the returned list will typically contain only one element.
    *   **One-to-Many:** If a single input context generates multiple output contexts, the returned list will be larger than the input list.
    *   **Many-to-Many:** The returned list’s size may not directly correlate with the input list’s size, reflecting a more complex merging relationship.
*   **Default:** `null` (no merging occurs).

**Important Considerations:**

The flexibility of the `merge()` method allows developers to implement sophisticated context aggregation strategies based on the requirements of their agentic flow. For example, you could use it to:

*   Combine data from parallel executions (depth 1).
*   Split a single context into multiple contexts based on certain criteria.
*   Filter or transform contexts during the merging process.
*   Create new contexts based on relationships between existing ones.

**10. `AgentNext chooseNextAgent(final AgentContext context)`**

*   **Purpose:** Determines which agent should be executed next in the flow based on the current `AgentContext`.
*   **Usage:** Implementations *must* override this method to implement the logic for selecting the next agent.  It should return an `AgentNext` enum value indicating either the name of the next agent or `AgentNext.END` to signal the end of the flow.
*   **Parameters:** `context`: The `AgentContext` object representing the current state of the agent flow.
*   **Returns:** An `AgentNext` enum value.
*   **Throws:** `UnsupportedOperationException` if not implemented.

## Important Considerations for Overall Agent Flow & AgentContext Flags

Beyond the individual methods, several parameters and flags within the `AgentContext` significantly influence the behavior of the overall agent flow. Developers should understand these to fine-tune their agents effectively. Here's a breakdown:

**1. `skipNextCompletion` Flag (Boolean):**

*   **Purpose:** Controls whether the LLM client (`client.conversation()` or `client.streamConversation()`) is called for the next turn in the conversation. This is useful for agents that perform processing *without* requiring an LLM completion.
*   **Usage:** Set this flag to `true` within an agent's `preProcess()` or `postProcess()` method if the agent only needs to manipulate the context and doesn’t require generating text from the LLM.  It's automatically reset after the completion attempt (or skip) within the `execute()` method.
*   **Impact:** Reduces API calls and processing time when an LLM interaction isn't necessary.

**2. `isStreaming` Flag (Boolean):**

*   **Purpose:** Enables or disables streaming responses from the LLM. Streaming provides incremental results as they become available, improving perceived responsiveness.
*   **Usage:** Set this flag in the `AgentContext` before calling `execute()`. Agents can also dynamically adjust this flag based on conditions during execution.
*   **Impact:** Affects how the response is handled by the `responseHandler()`. If `true`, a `StreamedAgentResponseHandler` is expected to handle chunks of data instead of a complete response. You can register a callback handler (Consumer) when calling AgentExecutorService.execute() to receive streamed responses.

**3. `conversation` (Conversation Object):**

*   **Purpose:** Holds the ongoing conversation history with the LLM.
*   **Usage:** Agents update the `conversation` object with each turn. You can read or manipulate the conversation as needed (usually for advanced scenarios).
*   **Impact:** Directly influences the LLM's behavior by providing conversational context.

**4. `requestStatistic` (Object tracking request details - implementation not shown):**

*   **Purpose:** Collects statistics about API requests made by the agent.
*   **Usage:**  The `execute()` method adds each `Completion` to this statistic tracker.
*   **Impact:** Helps monitor cost, performance, and identify potential issues with API calls.

**5. `error` Flag (Boolean):**

*   **Purpose:** Indicates whether an error occurred during the agent’s execution.
*   **Usage:** Set to `true` within `postProcess()` or during exception handling in `execute()`.
*   **Impact:** Used by the `execute()` method to filter out erroneous results and prevent further processing of failed branches.

**6. `graph` (Object representing the execution graph - implementation not shown):**

*   **Purpose:** A visual representation of the agent flow, showing the path taken through different agents and any branching that occurred.
*   **Usage:** Agents add nodes to the graph using methods like `add()`, indicating their execution and status.
*   **Impact:** Valuable for debugging, understanding complex flows, and visualizing the decision-making process.

**7. Custom Context Keys:**

*   **Purpose:** The `AgentContext` allows storing arbitrary data in its `context` map (a `HashMap`).
*   **Usage:** Developers can add custom keys and values to the context to share information between agents or track application-specific state.  This is heavily leveraged by the template system.
*   **Impact:** Enables highly flexible communication and data sharing within the agentic flow.

## Text Template Placeholders

**1. `{{{key}}}` - JSON String Replacement**

*   **Purpose:** This placeholder type expects the value associated with the given `key` in the `AgentContext` to be a valid JSON-serializable object (e.g., a string, number, boolean, list, or map).  The entire object will be converted into a JSON string using an `ObjectMapper` and inserted into the template.
*   **How it works:** The code iterates through the entries in the `AgentContext`'s context map (`context.getContext()`). If it finds a key matching the placeholder (e.g., `{{{myObject}}}`), it attempts to serialize the corresponding value into a JSON string using `objectMapper.writeValueAsString(entry.getValue())`.
*   **Example:**
    *   If `context.getContext().get("myObject")` returns `[1, 2, 3]`, then `{{{myObject}}}` would be replaced with `"[1, 2, 3]"`.
    *   If `context.getContext().get("userName")` returns `"Alice"`, then `{{{userName}}}` would be replaced with `"Alice"`.

**2. `{{key}}` - Text / List-to-String Conversion**

*   **Purpose:** This placeholder type expects the value associated with the given `key` in the `AgentContext` to be a *List* of objects. It joins the elements of this list into a single string, separated by newline characters (`\n\n`).
*   **How it works:** The code checks if `context.getContext().get(key)` returns a List. If so, it uses `entry.getValue().stream().map(Object::toString).collect(Collectors.joining("\n\n"))` to convert each element of the list to its string representation and concatenate them with double newlines.
*   **Example:**
    *   If `context.getContext().get("item")` returns `["apple"]`, then `{{item}}` would be replaced with:
        ```
        apple
        ```
    *   If `context.getContext().get("names")` returns `[1, 2, 3]`, then `{{names}}` would be replaced with:
        ```
        1
        2
        3
        ```

**3. `[[key]]` - Branched Template Expansion (Advanced)**

*   **Purpose:** This placeholder enables branching or looping within the template based on the size of a list stored in the `AgentContext`.  It's used to create multiple variations of the template, one for each element in the list.
*   **How it works:**
    *   The code identifies all keys using the `[[key]]` pattern.
    *   It determines the minimum size of all lists associated with these branched keys. This minimum size dictates how many "splits" are created.
    *   For each index from 0 up to the minimum size, it creates a new `Split` object. Each split has:
        *   A modified version of the template where `[[key]]` is replaced with the element at that index from each corresponding list.
        *   A new `AgentContext` containing only the selected elements from the branched lists.
    * Branched flows are executed separately and in parallel from this moment on. However, these flows can be combined again using the `AgentContext`'s `merge()` method. merge() only supports depth 1, this means that the merge() method can only combine the results of the immediate splits, not the results of the splits of those splits.
*   **Example:**
    *   If `context.getContext().get("[[cities]]")` returns `["London", "Paris", "Tokyo"]` and `context.getContext().get("[[countries]]")` returns `["UK", "France", "Japan"]`, then two splits will be created (because the minimum size is 2).
        *   **Split 1:** `text` will have `[[cities]]` replaced with `"London"` and `[[countries]]` replaced with `"UK"`. The context will contain only `{"cities": ["London"], "countries": ["UK"]}`.
        *   **Split 2:** `text` will have `[[cities]]` replaced with `"Paris"` and `[[countries]]` replaced with `"France"`. The context will contain only `{"cities": ["Paris"], "countries": ["France"]}`.

**In Summary:**

| Placeholder | Value Type Expected | Replacement Behavior                                      |
|-------------|---------------------|-----------------------------------------------------------|
| `{{{key}}}` | Any JSON-serializable | Serializes the entire value to a JSON string.           |
| `{{key}}}`  | List                | Joins list elements into a string with newlines. |
| `[[key]]`   | List                | Creates multiple template variations based on list size. |

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
