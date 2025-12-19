# lm-lib

The purpose of lm-lib is to provide a modular, extensible Java framework for interacting with large language models (LLMs) from various providers (such as OpenAI and local models). It abstracts away the complexities of prompt construction, conversation management, and model configuration, enabling developers to easily switch between models and providers. The library supports advanced features like streaming completions, request caching, statistics tracking, and agentic flow execution—allowing for the orchestration of multi-step, branching, and parallel conversational workflows. Integration with Spring ensures flexible configuration and dependency management, making it suitable for building scalable, maintainable, and sophisticated LLM-powered applications.

The libraries's key features include:

- Agentic flow execution
    - Dynamic prompt templates with placeholder substitution for injecting agent context data
    - Branching & merging (depth-1 parallel splits) with contextual graph tracing
- Streaming and non-streaming chat and text completions with reasoning support
- Unified API for multiple LLM providers (OpenAI, local models, etc.)  
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

### Managing Model Profiles

lm-lib offers three flexible approaches for managing model profiles:

#### 1. Predefined Categories (Recommended for Simple Cases)

Map model profiles to predefined categories using application properties:

    profile.model.s=local-small-model
    profile.model.m=llama3.3-70b-local
    profile.model.l=gpt-4
    profile.model.xl=gpt-4-turbo
    profile.model.hot=high-temperature-model
    profile.model.cold=deterministic-model

The value is the name of the model profile `.properties` file (without the extension), for example "llama3.3-70b-local".
**Note:** Model profile `.properties` files must be located in the classpath resources (e.g., `src/main/resources`). Examples can be found in test resources.

Agent and completion requests use predefined constants like `ModelCategory.MEDIUM` or `ModelCategory.LARGE`, which automatically load the corresponding model profile.

```java
// Using predefined categories
final var client = clientService.getClient(ModelCategory.MEDIUM);
final var completion = client.conversation(ModelCategory.LARGE, conversation);
```

#### 2. Custom Categories (Dynamic Profile Selection)

Create custom categories at runtime for more flexible profile management:

```java
// Create a custom category pointing to a specific profile
final var customCategory = new ModelCategory("profile-name", "CUSTOM");

// Use it just like predefined categories
final var client = clientService.getClient(customCategory);
final var completion = client.conversation(customCategory, conversation);
```

The value is the name of the model profile `.properties` file (without the extension), for example "llama3.3-70b-local".
**Note:** Model profile `.properties` files must be located in the classpath resources (e.g., `src/main/resources`). Examples can be found in test resources.

This approach is useful when you need to:
- Select profiles based on runtime conditions
- Create multiple variants of the same model with different parameters
- Implement A/B testing or model experimentation

#### 3. Programmatic ModelProfile Creation (Maximum Control)

Create `ModelProfile` instances directly in code, bypassing the properties file system entirely:

```java
// Build a ModelProfile programmatically
final var modelProfile = new ModelProfile(
    "",                                    // prompts (empty for chat completion)
    "cl100k_base",                        // tokenEncoding
    2000,                                 // maxTokens
    32768,                                // maxContextLength
    0.7,                                  // temperature
    1.0,                                  // topP
    ModelProfile.ModelProvider.OAI_CHAT_COMPLETION,
    "gpt-5.1",                            // modelName
    "https://api.openai.com/v1",         // url
    System.getenv("OPENAI_API_KEY"),     // apiKey
    new BigDecimal("1.25").divide(new BigDecimal(1000000)),  // costPerInToken ($1.25/1M)
    new BigDecimal("0.125").divide(new BigDecimal(1000000)), // costPerCachedInToken ($0.125/1M)
    new BigDecimal("10.0").divide(new BigDecimal(1000000)),  // costPerOutToken ($10.00/1M)
    BigDecimal.ZERO,                      // costPerReasoningOutToken
    null,                                 // reasoningEffort
    null,                                 // reasoningSummaryDetails
    "DYNAMIC"                             // category (short name for logging)
);

// Use the profile directly
final var client = clientService.getClient(modelProfile);
final var completion = client.conversation(modelProfile, conversation);

// Or in agents
final var agent = new Agent(
    "MyAgent",
    AgentType.START_CONVERSATION,
    null,              // modelCategory (not needed when using modelProfile)
    modelProfile,      // specify the profile directly
    "You are a helpful assistant.",
    "{{userInput}}",
    new TextAgentResponseHandler("response"),
    null, null, null,
    AgentNext::end
);
```

This approach is ideal for:
- Runtime model selection based on complex business logic
- Testing different model configurations without changing properties files
- Integrating with external configuration systems
- Per-request model customization

### Model Profile Properties

Each model profile is a `.properties` file that configures how the library interacts with a specific LLM. All properties are loaded from the classpath resources.

| Property | Type | Required | Description | Example Values |
|----------|------|----------|-------------|----------------|
| `category` | String | No | Short name identifier for the profile used in logging and graph visualization. If omitted, will be inferred from the ModelCategory when loading. | `M`, `L`, `CUSTOM`, `PROD` |
| `prompts` | String | Yes | Prompt template strategy name for text completions. Use empty string for chat completions or if no special formatting is needed. | `llama4`, `gpt-oss`, `no`, `` (empty) |
| `tokenEncoding` | String | Yes | Token encoding scheme used by the model for counting tokens. | `cl100k_base`, `none` |
| `maxTokens` | Integer | Yes | Maximum number of tokens to generate in the model response. | `1500`, `2500` |
| `maxContextLength` | Integer | Yes | Maximum total context length (prompt + response) supported by the model. | `16000`, `32767`, `128000` |
| `temperature` | Double | Yes | Controls randomness in responses. Lower values (0.0-0.5) are more deterministic, higher values (0.5-1.0+) more creative. | `0.2`, `0.4`, `0.7` |
| `topP` | Double | Yes | Nucleus sampling parameter. Controls diversity via cumulative probability. Typically kept at 1.0. | `1.0`, `0.9` |
| `modelProvider` | Enum | Yes | The LLM provider/client type to use. | `OAI_CHAT_COMPLETION`, `OAI_TEXT_COMPLETION` |
| `modelName` | String | No | The specific model identifier or deployment name. Can be empty for local models (e.g. lama-server) where it's auto-detected (use first name). | `Llama-3.3-70B-Instruct`, `gpt-4`, `` (empty for auto-detect) |
| `url` | String | Yes | Base URL endpoint for the model API. | `https://api.openai.com/v1`, `http://localhost:5001` |
| `apiKey` | String | No | Authentication key for the API. Can use Spring property placeholders like `${apikey}`. Can be empty for local models. | `sk-...`, `${apikey}`, `` (empty) |
| `cost1MInTokens` | Double | Yes | Cost per 1 million input tokens in your currency. Use `0` for free/local models. | `0.71`, `0`, `5.00` |
| `cost1MCachedInTokens` | Double | No | Cost per 1 million cached input tokens. Defaults to `cost1MInTokens` if not specified. | `0.35`, `0` |
| `cost1MOutTokens` | Double | Yes | Cost per 1 million output tokens in your currency. Use `0` for free/local models. | `0.71`, `0`, `15.00` |
| `cost1MReasoningOutTokens` | Double | No | Cost per 1 million reasoning output tokens. Defaults to `0` if not specified. | `0`, `60.00` |
| `reasoningEffort` | String | No | For reasoning models (e.g., OpenAI o1): controls depth of reasoning process. | `low`, `medium`, `high` |
| `reasoningSummaryDetails` | String | No | For reasoning models: controls how reasoning content is summarized in the response. | `auto`, `concise`, `detailed` |

**Notes:**
- Properties marked "Yes" in the Required column must be present in every profile; missing required properties will cause runtime errors.
- The `category` field is optional in profile files. When omitted, the category short name from the `ModelCategory` used to load the profile will be used instead.
- The `prompts` property determines the prompt formatting strategy. For text completions, specify a strategy name (e.g., `llama3`, `gpt-oss`) or `no` for no formatting. For chat completions, use an empty string.
- The `modelName` and `apiKey` properties are optional and can be empty for local model deployments. When `apiKey` is provided, it is sent as a Bearer token in the Authorization header.
- Cost properties are used for statistics tracking and have no impact on API calls. They help you monitor expenses.
- The `cost1MCachedInTokens` property defaults to `cost1MInTokens` if not specified, assuming the provider does not distinguish between cached and non-cached tokens. When the provider does support KV-cache pricing, cached tokens are typically charged at significantly lower rates (e.g., 10-20% of the regular input token cost).
- The `cost1MReasoningOutTokens` property defaults to `0` if not specified, as most models don't charge separately for reasoning tokens.
- Reasoning properties are only applicable to models that support separate reasoning output (like OpenAI's o1 series).

### Reasoning Models

For models that support reasoning (e.g., OpenAI's o1 series), you can configure reasoning parameters in the model profile:

    reasoningEffort=low|medium|high
    reasoningSummaryDetails=auto|concise|detailed

These settings control how the model generates and presents its reasoning process. The reasoning content is captured separately from the main response and can be accessed via the `reasoning` field in `Completion` objects or the `<key>_reasoning` context key in agents.

### Example Profile Files

**Local Chat Completion (local-chatcompletion.properties):**
```properties
category=M
prompts=
tokenEncoding=cl100k_base
maxTokens=2500
maxContextLength=16000
temperature=0.4
topP=1.0
modelProvider=OAI_CHAT_COMPLETION
modelName=
url=http://localhost:5001
apiKey=
cost1MInTokens=0
cost1MOutTokens=0
reasoningEffort=medium
reasoningSummaryDetails=auto
```

**Local Text Completion (local-completion.properties):**
```properties
category=S
prompts=gpt-oss
tokenEncoding=cl100k_base
maxTokens=2500
maxContextLength=16000
temperature=0.4
topP=1.0
modelProvider=OAI_TEXT_COMPLETION
modelName=
url=http://localhost:5001
apiKey=
cost1MInTokens=0
cost1MOutTokens=0
```

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

# Usage

1. Setup dependencies, see above
2. Configure model profiles, see above
3. Implement a CachedClientRepository if you need your requests cached
4. For chats or completions, use ClientService service bean
5. For agentic flows, create an AgentContext and use AgentExecutorService service bean

# Usage Examples

- Client service: clientService.getClient(ModelCategory.MEDIUM);
- Client usage details: CompletionClientTest
- Agentic flow: AgentTest#agenticFlow and AgentTest#streamedAgenticFlow

# Completions and Streaming

The library supports both streaming and non-streaming completions.

Streaming methods accept a `Consumer<Chunk>` to receive streaming updates. Each `Chunk` contains both `content()` and `reasoningContent()` fields, allowing models that support reasoning to stream their thought process separately from the final response. They also return a full `Completion` object or the final `AgentContext` at the end of the execution that also contains the statistics.

Non-streaming methods only return the final objects at the end of the execution. This approach is much more resource-friendly and therefore recommended if you don't need to display or process the results in real-time. The `Completion` record includes both `response()` and `reasoning()` fields.

# Completions Cache

All language model requests will be cached if you add a CachedClientRepository bean to your context.

# Completion Interceptors

You can intercept every Conversation and Completion by implementing `CompletionInterceptor`.
This is useful for accounting purposes, implementing circuit breakers, rate limiting, or logging.

Interceptor implementations receive the full `ModelProfile` for each request, allowing you to make decisions based on:
- The specific model being used
- Model costs and token limits
- Temperature and other parameters
- The model provider

Example:

```java
@Component
public class MyInterceptor implements CompletionInterceptor {
    
    @Override
    public Conversation before(ModelProfile modelProfile, Conversation conversation) {
        // Access model details
        LOGGER.info("Using model: {} at {}", modelProfile.modelName(), modelProfile.url());
        
        // Could implement rate limiting based on model category
        if ("EXPENSIVE".equals(modelProfile.category())) {
            checkRateLimit();
        }
        
        return conversation;
    }
    
    @Override
    public Completion after(ModelProfile modelProfile, Completion completion) {
        // Track costs based on actual model used
        trackCosts(modelProfile, completion.statistics());
        return completion;
    }
}
```

The client will autowire all `CompletionInterceptor` implementations and apply them to requests automatically.

# Graders

The library includes multiple grader implementations for evaluating the correctness and quality of responses. Graders can be used either as standalone utilities or integrated into agentic flows. Each grader uses a different evaluation approach and offers trade-offs between accuracy and performance.

**Performance Note:** The performance characteristics of agent graders vary significantly depending on the model type. Reasoning models have substantial inherent overhead, which means the additional complexity of agent-based approaches has relatively less impact on total execution time. Non-reasoning models show more pronounced relative differences because they have lower baseline overhead. All performance factors are relative to `Grader2` (baseline) and measured under identical conditions.

## Conventional Graders

Standalone graders for use outside agentic flows. These are simple utilities you can call directly from your code without requiring an `AgentContext`.

### Grader2 (Recommended for Conventional Grading)

**Approach:** Majority voting with multiple independent assessments.

Uses the grader logic multiple times and returns the majority result. This approach is slower but more robust and reliable, reducing the impact of individual LLM evaluation errors.

**Performance:** Baseline (1.0x)

**Feedback Loop:** Not supported (conventional grader)

**Use Case:** General-purpose binary evaluation (pass/fail) outside of agentic flows.

**Usage:**
```java
final var result = Grader2.assessScoreBinary("Does the text mention feature X?", textToGrade, clientService);
```

### Grader (Deprecated)

Legacy majority voting grader. Use `Grader2` instead.

## Agent Graders

Specialized graders designed to be integrated into agentic flows as part of an `AgentContext`. These agents can be composed within larger workflows and support feedback loops for iterative improvement.

### AnswerGrader2 (Recommended for Detailed Feedback in Flows)

**Approach:** Agentic evaluation with detailed scoring and feedback loop.

An agent-based grader that provides a detailed rating (0-100) for each answer along with an explanation. Supports optional descriptions via the `qaOutputDescription` context key. Includes a feedback loop mechanism (up to 2 attempts) to improve scoring accuracy when initial ratings are below threshold (≤60).

**Performance:**
- **With descriptions:** ~1.5x - 7x slower than baseline — provides detailed explanations
- **Without descriptions:** ~1.2x - 2.3x slower than baseline — faster, ratings only

*Note: Performance varies significantly by model type. Reasoning models show minimal relative overhead (~1.5x/1.2x) due to their substantial baseline processing time, while non-reasoning models show greater relative differences (~7x/2.3x).*

**Feedback Loop:** Supported (retries up to 2 attempts when rating ≤60)

**Use Case:** Quality assessment within agentic flows where detailed feedback and scoring are required, with optional retry logic for borderline cases.

**Context Keys:**
- `qaQuestion`: The question to evaluate
- `qaAnswer`: The answer to grade
- `qaOutputDescription`: Set to `Boolean.TRUE` (default) for detailed explanations or `Boolean.FALSE` for ratings only (required for feedback loop to provide meaningful feedback)
- `qaFeedbackLoopDestination` (required for feedback loop): Agent name to return to for answer refinement

**Usage:**
```java
context.setStringValue("qaQuestion", "Is this a good solution?");
context.setStringValue("qaAnswer", answerText);
context.setValue("qaOutputDescription", Boolean.FALSE); // For faster evaluation
context.setStringValue("qaFeedbackLoopDestination", "AnswerRefiner");
final var result = agentExecutorService.walk("AnswerGrader2", context);
```

### AnswerGraderBinary (Recommended for Speed in Flows)

**Approach:** Agentic binary evaluation with feedback loop.

A lightweight agent-based grader that determines if an answer is a successful resolution (true/false) with optional explanations. Includes a feedback loop mechanism (up to 2 attempts) for improved accuracy when evaluations fail.

**Performance:**
- **With descriptions:** ~1.3x - 6.3x slower than baseline — includes brief explanations
- **Without descriptions:** ~1.0x - 2.2x slower than baseline — fastest agent grader option

*Note: Performance varies significantly by model type. Reasoning models show minimal relative overhead (~1.3x/1.0x) due to their substantial baseline processing time, while non-reasoning models show greater relative differences (~6x/2x).*

**Feedback Loop:** Supported (retries up to 2 attempts when evaluation fails)

**Use Case:** Quick pass/fail evaluation within agentic flows where speed is prioritized, with optional feedback for failed evaluations.

**Context Keys:**
- `qaQuestion`: The question to evaluate
- `qaAnswer`: The answer to evaluate
- `qaOutputDescription`: Set to `Boolean.TRUE` (default) for explanations or `Boolean.FALSE` for boolean only (required for feedback loop to provide meaningful feedback)
- `qaFeedbackLoopDestination` (required for feedback loop): Agent name to return to for answer refinement

**Usage:**
```java
context.setStringValue("qaQuestion", "Does this answer the question?");
context.setStringValue("qaAnswer", answerText);
context.setValue("qaOutputDescription", Boolean.FALSE); // For maximum speed
context.setStringValue("qaFeedbackLoopDestination", "AnswerRefiner");
final var result = agentExecutorService.walk("AnswerGraderBinary", context);
```

### AnswerGrader (Deprecated)

Legacy agent-based grader. Use `AnswerGrader2` instead.

## Feedback Loop Mechanism in Agent Graders

Both `AnswerGrader2` and `AnswerGraderBinary` support an optional feedback loop for iterative improvement within agentic flows. The feedback loop provides actionable feedback to the source agent to refine their answer. When configured:

1. If the evaluation fails and fewer than 2 attempts have been made, the grader:
   - Constructs feedback from the previous evaluation (using `qaExplanation` from the grader output)
   - Returns to the specified feedback loop destination (the source agent) with a retry flag
   - The source agent receives the feedback via the `qaFeedback` context key and can refine the answer
   - The refined answer is resubmitted for evaluation

2. After 2 attempts or if the evaluation passes, evaluation completes with the final result

**Note:** For the feedback loop to provide meaningful feedback, `qaOutputDescription` should be set to `Boolean.TRUE` to ensure the grader generates explanations that can guide the source agent's refinement.

**Example with Feedback Loop:**
```java
// Set up an agentic flow with a feedback loop
context.setStringValue("qaQuestion", "Explain machine learning");
context.setStringValue("qaAnswer", initialAnswer);
context.setStringValue("qaFeedbackLoopDestination", "AnswerEnhancer");
final var result = agentExecutorService.walk("AnswerGraderBinary", context);
// If grading fails, will loop back to AnswerEnhancer for one retry
```

# Agentic Flows

This library contains a graph executor for implementing comprehensive processes using agent-based design patterns. 

You can implement your Agents either by creating an Agent record or by extending BaseAgent - choose the approach that best fits your needs.
The agentic flow is started with an AgentContext and an initial agent.
Example:

    final var context = new AgentContext(List.of(new Agent1(), new Agent2()));
    // Passing an explicit agent list is optional. If you omit the list, the framework will autowire all Agent beans from the Spring context:
    // final var context = new AgentContext();
    context.getContext().setStringValue("key", "Payload here");
    context.getContext().setStringValues("key2", List.of("All context keys", "support collections", "for aggregation purposes"));
    context.getContext().setValue("key3", new RandomDatatype());
    final var resultContexts = agentExecutorService.walk("Agent1", context)
    final var resultContext = resultContexts.getFirst(); // You may receive multiple contexts if your graph branches out
    LOGGER.info(resultContext.getStringValue("blogPost") + "\n\n" + resultContext.getGraph().toString() + "\n" + resultContext.getRequestStatistic().toString());

Streaming is also supported.

See class AgentTest for examples.

## Quick Agent Implementation Examples

### Example 1: BaseAgent Implementation (Comprehensive)

This example shows all methods that can be overridden in a `BaseAgent` implementation:

```java
public class BlogPostAgent implements BaseAgent {
    
    // REQUIRED: Unique identifier for this agent
    @Override 
    public String name() { 
        return "BlogPostAgent"; 
    }
    
    // REQUIRED: Determines how conversation is managed
    @Override 
    public AgentType agentType(final AgentContext ctx) { 
        return AgentType.START_CONVERSATION_WITH_SYSTEM_PROMPT; 
    }
    
    // OPTIONAL: Model size/capability (default: MEDIUM)
    @Override 
    public ModelCategory modelCategory(final AgentContext ctx) { 
        return ModelCategory.MEDIUM; 
    }
    
    // OPTIONAL: Specific model profile (overrides modelCategory, default: null)
    @Override 
    public ModelProfile modelProfile(final AgentContext ctx) { 
        // Return null to use modelCategory() instead
        // Or return a specific ModelProfile for direct control
        return null; 
    }
    
    // OPTIONAL: System-level instruction for the LLM (default: null)
    @Override 
    public String systemPrompt() { 
        return "You are a concise technical blog writer."; 
    }
    
    // OPTIONAL: Template for user input with placeholders (default: null)
    @Override 
    public String textTemplate() { 
        return "Write a blog post about {{topic}} using these bullets:\n\n{{points}}"; 
    }
    
    // OPTIONAL: How to parse/store the response (default: TextAgentResponseHandler("response"))
    @Override 
    public AgentResponseHandler responseHandler(final AgentContext ctx) { 
        return new TextAgentResponseHandler("blogPost"); 
    }
    
    // OPTIONAL: Execute before LLM call (default: no-op)
    @Override 
    public void preProcess(final AgentContext ctx) {
        // Example: Skip LLM call for pure processing steps
        // ctx.setSkipNextCompletion(true);
        
        // Example: Manipulate context data
        // ctx.getContext().setStringValue("processedTopic", 
        //     ctx.getContext().getStringValue("topic").toUpperCase());
    }
    
    // OPTIONAL: Execute after LLM call (default: no-op)
    @Override 
    public void postProcess(final AgentContext ctx) {
        // Example: Clean up or transform results
        // String post = ctx.getContext().getStringValue("blogPost");
        // ctx.getContext().setStringValue("wordCount", String.valueOf(post.split("\\s+").length));
    }
    
    // OPTIONAL: Merge multiple contexts after branching (default: null = no merge)
    @Override 
    public List<AgentContext> merge(final List<AgentContext> contexts) {
        // Example: Many-to-one merge using helper
        // return BaseAgent.mergeContexts(List.of("blogPost", "ideas"), contexts);
        
        // Return null to keep contexts separate (no merge)
        return null;
    }
    
    // OPTIONAL: Select next agent in the flow (default: AgentNext.END)
    @Override 
    public AgentNext chooseNextAgent(final AgentContext ctx) {
        // Example: Conditional branching
        // if (ctx.getContext().getStringValue("blogPost").length() < 100) {
        //     return new AgentNext("ExpandAgent");
        // }
        
        // End the flow
        return AgentNext.END;
    }
}
```

**Usage:**
```java
final var context = new AgentContext(List.of(new BlogPostAgent()));
context.getContext().setStringValue("topic", "Java Concurrency");
context.getContext().setStringValues("points", List.of("Thread safety", "CompletableFuture", "Virtual threads"));
final var results = agentExecutorService.walk("BlogPostAgent", context);
final var blogPost = results.getFirst().getContext().getStringValue("blogPost");
```

### Example 2: Agent Record Implementation (Minimal)

The `Agent` record provides a concise alternative when you prefer composition over inheritance:

```java
// Simple agent using record constructor
final var blogPostAgent = new Agent(
    "BlogPostAgent",                                    // name
    AgentType.START_CONVERSATION_WITH_SYSTEM_PROMPT,   // agentType
    ModelCategory.MEDIUM,                               // modelCategory
    null,                                               // modelProfile (null to use modelCategory)
    "You are a concise technical blog writer.",        // systemPrompt
    "Write a blog post about {{topic}} using these bullets:\n\n{{points}}", // textTemplate
    new TextAgentResponseHandler("blogPost"),           // responseHandler
    null,                                               // preProcessor (optional)
    null,                                               // postProcessor (optional)
    null,                                               // merger (optional)
    ctx -> AgentNext.END                                // chooseNext
);

// Agent with processing hooks
final var processingAgent = new Agent(
    "DataProcessor",
    AgentType.PROCESSING_ONLY,
    ModelCategory.MEDIUM,
    null,                                               // modelProfile
    null,
    null,
    new TextAgentResponseHandler("result"),
    ctx -> {
        // preProcessor: prepare data
        var input = ctx.getContext().getStringValue("rawData");
        ctx.getContext().setStringValue("cleaned", input.trim().toLowerCase());
        ctx.setSkipNextCompletion(true); // No LLM needed
    },
    ctx -> {
        // postProcessor: finalize
        ctx.getContext().setStringValue("processed", "true");
    },
    null,
    ctx -> new AgentNext("NextAgent")
);

// Agent with branching and merging
final var analyzerAgent = new Agent(
    "FeatureAnalyzer",
    AgentType.START_CONVERSATION,
    ModelCategory.SMALL,
    null,                                               // modelProfile
    null,
    "Analyze this feature: [[feature]]",  // Branches per feature
    new JsonAgentResponseHandler("analysis_"),
    null,
    null,
    contexts -> BaseAgent.mergeContexts(List.of("analysis_score", "analysis_summary"), contexts), // Many-to-one merge
    ctx -> new AgentNext("ReportGenerator")
);

// Agent using a programmatically created ModelProfile
final var customProfile = new ModelProfile(
    "",                                               // prompts
    "cl100k_base",                                    // tokenEncoding
    3000,                                             // maxTokens
    64000,                                            // maxContextLength
    0.3,                                              // temperature
    1.0,                                              // topP
    ModelProfile.ModelProvider.OAI_CHAT_COMPLETION,   // modelProvider
    "gpt-5.1",                                        // modelName
    "https://api.openai.com/v1",                     // url
    System.getenv("OPENAI_API_KEY"),                 // apiKey
    new BigDecimal("1.25").divide(new BigDecimal(1000000)),  // costPerInToken ($1.25/1M)
    new BigDecimal("0.125").divide(new BigDecimal(1000000)), // costPerCachedInToken ($0.125/1M)
    new BigDecimal("10.0").divide(new BigDecimal(1000000)),  // costPerOutToken ($10.00/1M)
    BigDecimal.ZERO,                                  // costPerReasoningOutToken
    null,                                             // reasoningEffort
    null,                                             // reasoningSummaryDetails
    "GPT51"                                           // category
);

final var customModelAgent = new Agent(
    "CustomModelAgent",
    AgentType.COMPLETION,
    null,                                             // modelCategory (not needed with modelProfile)
    customProfile,                                    // modelProfile (overrides category)
    "Expert technical writer",
    "Summarize: {{text}}",
    new TextAgentResponseHandler("summary"),
    null, null, null,
    AgentNext::end
);
```

**Usage:**
```java
final var context = new AgentContext(List.of(blogPostAgent, processingAgent, analyzerAgent));
context.getContext().setStringValue("topic", "Microservices");
context.getContext().setStringValues("points", List.of("Scalability", "Independence", "Resilience"));
final var results = agentExecutorService.walk("BlogPostAgent", context);
```

**Choosing Between BaseAgent and Agent Record:**
- **BaseAgent class**: Better for complex agents with substantial logic in lifecycle methods, better IDE support for method overrides
- **Agent record**: Better for simple agents, more concise, easier to create dynamically or configure from data

## Agent Types & Lifecycle

`AgentType` drives how the conversation is (re)constructed before a request and how the next agent chaining behaves:

| Type | Conversation Behavior | Typical Use |
|------|-----------------------|-------------|
| `START_CONVERSATION` | Starts a fresh conversation (user-only first turn, merges prior system if present) | New thread without system prompt reuse |
| `START_CONVERSATION_WITH_SYSTEM_PROMPT` | Starts fresh with `systemPrompt()` + first user turn | Strict instruction resets |
| `CONVERSATION` | Continues existing conversation (adds user turn) | Multi-turn iterative refinement |
| `CONVERSATION_WITH_SYSTEM_PROMPT` | Keeps a distinct system turn at index 0 | When persistent system role required |
| `COMPLETION` | Stateless single shot (conversation nulled before call) | One-off transformation / generation |
| `PROCESSING_ONLY` | No LLM call (graph marks circle) | Data shaping, filtering, grading, enrichment |

Lifecycle per split:
1. `preProcess()` (can mutate context, set `skipNextCompletion`)
2. Template expansion (may create N parallel splits)
3. (Optional) LLM request unless skipped or processing-only
4. Response handling with up to 2 parse attempts (retry if handler returns `false` / throws)
5. `postProcess()`
6. Merge (single depth) via `merge()` if provided
7. Next agent selection via `chooseNextAgent()`


## Template Placeholders

| Placeholder | Expects | Behavior |
|-------------|---------|----------|
| `{{{key}}}` | Any JSON-serializable value (list, map, scalar) | JSON serialization inserted verbatim |
| `{{key}}` | List | Joins list elements with double newlines |
| `[[key]]` | List | Branching: visible replacement with ith element |
| `[[key\|hidden]]` | List | Branching: element participates (context narrowed) but prompt text omitted |

Hidden variant example:

Template: `Analyze these items. Visible: [[name]] Hidden id used for internal scoring. [[id|hidden]]`

For lists `name=[Alpha, Beta]`, `id=[42, 43]` two splits are produced:
1. Prompt contains `Visible: Alpha Hidden id used ...` (id removed) and context key `id` = `[42]`
2. Prompt contains `Visible: Beta Hidden id used ...` (id removed) and context key `id` = `[43]`

Use hidden placeholders when you need parallelization keyed by an internal identifier you do not want to expose to the model (e.g. database id, sensitive tag) but still require for later merging or grading.

## Branching & Merging

Use `[[key]]` placeholders to branch; each placeholder must map to a list. The minimum list size across all branched keys defines the number of splits. Each split receives only the single element for those keys (facilitating isolation for later merging). Branching depth currently supported: only the immediate level (depth 1) created by one template application is eligible for a single merge phase—splits of splits are treated independently; you can implement higher-level aggregation manually in later agents.

Custom merging strategies:
* Return `null` => keep individual split contexts
* Return list with 1 element => many-to-one consolidation
* Return bigger list => one-to-many expansion (e.g., combinatorial synthesis)

Utility helper supplied: `BaseAgent.mergeContexts(keys, contexts)` merges list-valued keys into the first context by concatenating.

## Execution Graph Legend

The `graph` in `AgentContext` accumulates symbols documenting the execution flow. These symbols provide a visual trace of the agent pipeline, showing model usage, branching/merging, and any issues encountered:

| Symbol | Meaning |
|--------|---------|
| `M` / `S` / `L` / `HOT` / `COLD` | Model category short name (Medium, Small, Large, Hot, Cold) |
| Agent name | Name of the executed agent (e.g., `CodeSummarizer`, `BlogPostAgent`) |
| `○` | Completion skipped (either `skipNextCompletion` flag or `PROCESSING_ONLY` agent type) |
| `⇉ x/n` | Branch split index x of n (parallel execution from template placeholder branching) |
| `size↣merged` | Result of merge phase showing input→output count (e.g., `5↣1` means 5 contexts merged into 1) |
| `→` | Next agent is of type `COMPLETION` (stateless single-shot, conversation cleared) |
| `🗨` | Next agent starts a new conversation (conversation history cleared) |
| `↴` | Continue existing conversation (assistant turn appended if missing, maintains context) |
| `✓` / `✗` | Grading result (pass/fail) when `AgentNext.gradingPassed()` is present |
| `⚠` | Parse attempt failed (retry will follow, max 2 attempts total) |
| `☢` | Fatal error: parse failure after all retries, unknown agent, or agent not found |
| `‖` | Agent execution continuation (appears when calling `walk()` on a non-empty graph) |

**Example graph trace:**
```
M, CodeSummarizer, ↴, M, FeatureIdentifier, →, ⇉ 1/5, M, FeatureAnalyzer, 5↣1, →, M, AnswerGenerator
```
This shows: Medium model used for CodeSummarizer → conversation continued to FeatureIdentifier → branches into 5 parallel splits → each split analyzed with FeatureAnalyzer → merged back to 1 context → final answer generated.

## Retry & Parse Handling

Each agent completion is parsed by a fresh `responseHandler()` instance (created per attempt). If `handle()` returns `false` or throws, the framework retries once (total 2 attempts). After the final failure the context is marked error (`setError(true)`), receives `☢`, and is excluded from merges / next-agent chaining.

## Built-in Response Handlers

Response handlers implement `AgentResponseHandler` (or `StreamedAgentResponseHandler`) and transform raw model output into structured context keys. A handler returning `false` triggers a retry (up to one additional attempt).

### TextAgentResponseHandler
Purpose: Capture the full response (optionally transformed) under a single key. Implements streaming with support for reasoning content.
Constructor variants:
* `new TextAgentResponseHandler(key)`
* `new TextAgentResponseHandler(key, transformer, beforeStream, chunkTransformer, afterChunkUpdate)`

Note: `chunkTransformer` signature is `Function<Chunk, Chunk>` and `afterChunkUpdate` signature is `BiConsumer<AgentContext, Chunk>`.

Streaming lifecycle:
1. `beforeStream()` sets the target key to empty string and invokes optional `beforeStream` callback.
2. For each chunk: `handleChunk()` receives a `Chunk` object, optionally transforms it, appends `content()` to the aggregated key and `reasoningContent()` to `<key>_reasoning`, then invokes `afterChunkUpdate` with both context and chunk.
3. After stream completion the final `Completion` is passed to `handle()` (allowing a final transformer pass / overwrite).

Context keys during streaming:
* `<key>`: aggregated content so far
* `<key>_reasoning`: aggregated reasoning content (if model provides it)

Both reasoning and response content are logged when available.

Use cases: plain text generation, incremental UI updates, progressive summarization, observing model reasoning process.

### AccumulateTextAgentResponseHandler
Purpose: Append each response to a list under the provided key (e.g. collecting multiple answers or iterative drafts).
Behavior: On every `handle()` call, transforms (optional) then `add()`s the response string to an ArrayList stored at the key. Always returns `true` (no retry).
Use cases: multi-pass refinement pipelines, collecting alternatives, voting ensembles.

### JsonAgentResponseHandler
Purpose: Parse loosely formatted JSON from the model response and flatten into list-valued context keys (optionally with a field name prefix).
Behavior:
1. Locates first '{' and last '}' substring segment.
2. Attempts to parse with permissive Jackson settings (allows trailing commas, single quotes, comments, unquoted field names, etc.).
3. Flattens arrays / nested objects: scalar primitives become entries; arrays of objects are traversed recursively; complex object properties yield all textual/primitive leaf values.
4. On success: Inserts (or appends to) `List<String>` values keyed by `[prefix]fieldName`.
5. On failure: Returns `false` to trigger retry and augments the last user turn with an instruction requesting valid JSON.

Customization: Provide `new JsonAgentResponseHandler("myPrefix_")` to namespace parsed fields.

Use cases: structured extraction, slot filling, multi-field generation, classification outputs.

### Choosing a Handler
| Scenario | Recommended Handler | Notes |
|----------|---------------------|-------|
| Streamed narrative / article | TextAgentResponseHandler | Use chunk callbacks for live UI |
| Collect multiple alternatives | AccumulateTextAgentResponseHandler | Pair with downstream ranking/grader |
| Extract structured fields | JsonAgentResponseHandler | Ensure prompt asks for strict JSON |
| Hybrid (JSON + plain text) | Separate agents or custom composite | You can chain: JSON parse then text refine |

### Writing a Custom Handler
Implement `AgentResponseHandler` (and optionally `StreamedAgentResponseHandler` for chunk support). Guidelines:
* Keep parsing deterministic; return `false` only on recoverable format issues.
* Avoid long-running logic; heavy processing should go into a `PROCESSING_ONLY` agent after data capture.
* Use distinct context keys; never overwrite model trace keys unless intentional.

## Streaming Considerations

If `context.isStreaming()` is `true` and the handler implements `StreamedAgentResponseHandler`, chunk callbacks occur during the request. After streaming completes the full `Completion` object is still assigned and final parse is executed (allowing hybrid incremental + final processing). Ensure idempotency in `handleChunk()`—it may be called many times.

## Skipping LLM Calls

Set `context.setSkipNextCompletion(true)` in `preProcess()` (or earlier) to mark a single upcoming step as processing-only. The flag auto-resets. The graph will show `○` for that node; no tokens are consumed and no retry loop runs.

## Error Handling

Exceptions inside an agent split mark only that split as errored; others continue. Post-merge, errored contexts are appended to the returned list (after successful ones) so callers can inspect failures without losing success results.

## Helper Patterns

Pattern: Processing + generation pipeline
1. Processing agent (`PROCESSING_ONLY`) enriches context keys
2. Generation agent (`START_CONVERSATION_WITH_SYSTEM_PROMPT`) creates initial draft
3. Refinement agent (`CONVERSATION`) iterates until quality threshold (grading-based branching)

Pattern: Fan-out / Fan-in
1. Branch agent uses template with multiple `[[item]]` placeholders
2. Analysis agents run in parallel per branch
3. Merge via custom `merge()` consolidating lists / selecting best result

## Best Practices

* Keep `responseHandler()` pure per attempt—avoid retaining mutable internal state between attempts.
* Use distinct context keys for raw response vs. parsed fields (`response`, `blogPost`, `scores`, etc.).
* Employ `skipNextCompletion` for cheap structural steps (classification using already available data, etc.).
* When merging, explicitly document transformation size with graph annotation already produced automatically (`size↣merged`).
* Prefer smaller model categories (`S`,`M`) for early filtering agents; escalate to larger (`L`) only when necessary.
* Use grading to gate expensive downstream calls (`✓` / `✗`).

---

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

**3. `ModelCategory modelCategory(final AgentContext context)`**

*   **Purpose:** Specifies the desired size/capability category of the language model to use when interacting with the LLM client. This allows for cost optimization or performance tuning by selecting models appropriate for the task. This method is only consulted if `modelProfile()` returns `null`.
*   **Usage:** Implementations can optionally override this method to customize the model category. The default implementation returns `ModelCategory.MEDIUM`.
*   **Parameters:** `context`: The `AgentContext` object representing the current state of the agent flow.
*   **Returns:** A `ModelCategory` value (can be predefined like `ModelCategory.MEDIUM` or a custom instance).
*   **Default:** `ModelCategory.MEDIUM`

**4. `ModelProfile modelProfile(final AgentContext context)`**

*   **Purpose:** Directly specifies a `ModelProfile` to use for this agent, bypassing the model category system. When this method returns a non-null value, it takes precedence over `modelCategory()`. This provides maximum control over model selection, allowing runtime-specific profile creation or selection based on context.
*   **Usage:** Implementations can optionally override this method to return a specific `ModelProfile` instance. This is useful for agents that need to use a specific model configuration that doesn't fit into the predefined categories, or when model selection needs to be dynamic based on the agent context.
*   **Parameters:** `context`: The `AgentContext` object representing the current state of the agent flow.
*   **Returns:** A `ModelProfile` instance, or `null` to use `modelCategory()` instead.
*   **Default:** `null`

**5. `String systemPrompt()`**

*   **Purpose:** Provides a system-level instruction that sets the context and behavior for the LLM. This prompt is prepended to user input when creating a conversation.
*   **Usage:** Implementations can optionally override this method to provide a custom system prompt tailored to their specific task. If no prompt is needed, it should return `null`.
*   **Returns:** A `String` containing the system prompt.
*   **Default:** `null`

**6. `String textTemplate()`**

*   **Purpose:** Defines a template string for generating input text for the LLM.  The template can include placeholders (e.g., `{{key}}`) which are dynamically replaced with values from the `AgentContext`. `[[key]]` style placeholders are a convenient way to branch out / parallelize execution flows. See also the section about text template placeholders below.
*   **Usage:** Implementations can optionally override this method to define a template. This is useful for constructing prompts based on data in the agent context.
*   **Returns:** A `String` containing the text template.
*   **Default:** `null`

**7. `AgentResponseHandler responseHandler()`**

*   **Purpose:**  Provides a mechanism for processing the raw LLM response. Different handlers can parse responses into structured data, extract specific information, or perform validation.
*   **Usage:** Implementations can optionally override this method to return a custom `AgentResponseHandler`. The default implementation returns a `TextAgentResponseHandler` configured to handle responses under the "response" key.
*   **Returns:** An `AgentResponseHandler` instance.
*   **Default:** `new TextAgentResponseHandler("response")`

**8. `void preProcess(final AgentContext context)`**

*   **Purpose:** Allows performing actions *before* the main logic of the agent is executed. This could involve modifying the `AgentContext`, preparing data, or setting up necessary resources.
*   **Usage:** Implementations can optionally override this method to add pre-processing steps.
*   **Parameters:** `context`: The `AgentContext` object representing the current state of the agent flow.

**9. `void postProcess(final AgentContext context)`**

*   **Purpose:** Allows performing actions *after* the main logic of the agent is executed. This could involve cleaning up resources, updating the `AgentContext`, or logging results.
*   **Usage:** Implementations can optionally override this method to add post-processing steps.
*   **Parameters:** `context`: The `AgentContext` object representing the current state of the agent flow.

**10. `List<AgentContext> merge(final List<AgentContext> contexts)`**

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

**11. `AgentNext chooseNextAgent(final AgentContext context)`**

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
