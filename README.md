# lm-lib

lm-lib is a modular Java library for building LLM-powered applications with a strong focus on agentic orchestration. It has two main responsibilities:

- An agent runtime for prompt templating, conversation orchestration, branching, merging, grading, and execution tracing
- A client layer for model profiles, chat and text completions, streaming, reasoning capture, token and cost accounting, caching, retries, and interceptors

The library exists to keep both multi-step orchestration and model integration out of application code. Instead of manually wiring agent flow control, prompt formatting, cache checks, token counting, and REST requests in every project, you compose agents and clients while lm-lib handles the repetitive infrastructure.

lm-lib is published under the Apache License 2.0.

## Overview

lm-lib is designed for applications that need more than single prompt-response calls. Its center of gravity is the agent framework: structured multi-step flows such as classification, planning, fan-out analysis, synthesis, grading, feedback loops, and other graph-based execution patterns.

It also supports direct completion use cases through a client abstraction layer. The built-in clients currently target OpenAI-compatible endpoints, including local servers that expose the same API shape, but the library is intentionally structured so additional client implementations can be added over time. Applications can also provide their own client implementations by implementing the relevant interfaces.

The current built-in client set supports both chat completions and legacy text completions, with model-specific prompt strategies for text completion models.

## Key Capabilities

- OpenAI-compatible chat and text completions
- Chat-completion tool calling with tool definitions, returned tool calls, and tool-result follow-up rounds
- Streaming and non-streaming APIs
- Separate reasoning capture for models that expose reasoning output
- Model profile loading from properties or direct programmatic profile creation
- Category-based model selection and runtime custom categories
- Token counting with pluggable tokenizer strategies
- Request statistics including cached tokens, reasoning tokens, duration, throughput, and cost
- Optional completion caching with per-request cache bypass controls
- Completion interceptors for logging, accounting, rate limiting, and guardrails
- Agentic execution with conversation management, branching, merging, retries, and graph tracing
- Built-in response handlers for text, accumulation, JSON, CSV, and CSV-to-object unmarshalling
- Conventional graders and agent graders with optional feedback loops
- Spring Boot auto-configuration with selective module enablement

## Requirements

- Java 25 or higher
- Spring Boot 4.0 or higher

## Setup

The published artifacts are hosted on Maven Central, so no additional repository configuration is required:

```xml
<dependency>
    <groupId>de.extio</groupId>
    <artifactId>lm-lib</artifactId>
    <version>2.5.13</version>
</dependency>
```

### Mandatory Dependency

The library requires Spring Boot. The library intentionally keeps most dependencies in `provided` scope so consuming applications can control their own dependency tree.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-restclient</artifactId>
</dependency>
```

## Configuration

The library is split into auto-configured modules that can be enabled or disabled independently.

### Module Enable/Disable Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `lmlib.profile.enabled` | Boolean | `true` | Enables model profile loading and resolution. |
| `lmlib.tokenizer.enabled` | Boolean | `true` | Enables tokenizer beans for token counting and fallback token estimation. |
| `lmlib.restclient.enabled` | Boolean | `true` | Enables REST client configuration for HTTP communication. |
| `lmlib.prompt.enabled` | Boolean | `true` | Enables built-in prompt strategies for text completion models. |
| `lmlib.client.enabled` | Boolean | `true` | Enables completion clients. Depends on profile, tokenizer, and restclient modules. |
| `lmlib.agent.enabled` | Boolean | `true` | Enables the agent executor. Depends on the client module. |

If you disable a module that another module depends on, you must provide the missing beans yourself.

### REST Client Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `lmlib.client.proxy.enabled` | Boolean | `false` | Enables an HTTP proxy for LLM requests. |
| `lmlib.client.proxy.host` | String | | Proxy host. |
| `lmlib.client.proxy.port` | Integer | `0` | Proxy port. |
| `lmlib.client.proxy.user` | String | | Proxy username. |
| `lmlib.client.proxy.password` | String | | Proxy password. |
| `lmlib.client.tls.verification.disabled` | Boolean | `false` | Disables TLS certificate verification. Not recommended in production. |
| `lmlib.client.retry.max-attempts` | Integer | `5` | Maximum retry attempts for failed HTTP calls. |
| `lmlib.client.retry.backoff-interval-min` | Long | `125` | Minimum retry backoff in milliseconds. |
| `lmlib.client.retry.backoff-interval-max` | Long | `2500` | Maximum retry backoff in milliseconds. |

## Optional Dependencies

### Caching Support

If you want Spring cache support for services such as model profile loading or client retrieval, add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

Then provide a `CacheManager`:

```java
@Bean
public CacheManager cacheManager() {
    return new CaffeineCacheManager();
}
```

### Tokenizer Dependencies

For the recommended `jTokkit` tokenizer:

```xml
<dependency>
    <groupId>com.knuddels</groupId>
    <artifactId>jtokkit</artifactId>
</dependency>
```

For `llamaServer`, no extra dependency is required because tokenization is delegated to the remote server through the configured model endpoint.

Available built-in tokenizer names:

- `jTokkit`
- `llamaServer`
- `fallback`

### Tokenizer Configuration

lm-lib registers tokenizer implementations as beans when their dependencies are available, then resolves the tokenizer at runtime from the active `ModelProfile`. Applications can implement the `Tokenizer` interface to inject their own Tokenizers.

Tokenizer selection is configured per model profile with the `tokenizer` property. If a profile does not specify a tokenizer, lm-lib prefers `jTokkit` when the dependency is present and otherwise falls back to the built-in `fallback` tokenizer. The fallback tokenizer is deterministic and reversible, but it only provides approximate token counts.

## Model Profiles

Model profiles describe how a request should be sent to a model and how its usage should be accounted for. They can be loaded from `.properties` files, selected dynamically at runtime, or created programmatically.

### 1. Predefined Categories

Use properties to map model categories to profile files:

```properties
profile.model.s=local-small-model
profile.model.m=llama3.3-70b-local
profile.model.l=gpt-4
profile.model.xl=gpt-4-turbo
profile.model.hot=high-temperature-model
profile.model.cold=deterministic-model
```

Each value refers to a profile file on the classpath without the `.properties` suffix.

```java
final var client = clientService.getClient(ModelCategory.MEDIUM);
final var completion = client.conversation(ModelCategory.LARGE, conversation, null, false);
```

### 2. Custom Categories

Create categories dynamically when predefined ones are too restrictive:

```java
final var customCategory = new ModelCategory("profile-name", "CUSTOM");

final var client = clientService.getClient(customCategory);
final var completion = client.conversation(customCategory, conversation, null, false);
```

This is useful for runtime routing, model experiments, or per-tenant profile selection.

### 3. Programmatic ModelProfile Creation

Create a `ModelProfile` directly when you want full runtime control and do not want to depend on properties files:

```java
final var modelProfile = new ModelProfile(
    "",                                    // prompts
    "jTokkit",                            // tokenizer
    "cl100k_base",                        // tokenEncoding
    2000,                                  // maxTokens
    32768,                                 // maxContextLength
    0.7,                                   // temperature
    1.0,                                   // topP
    ModelProfile.ModelProvider.OAI_CHAT_COMPLETION,
    "gpt-5.1",                            // modelName
    "https://api.openai.com/v1",          // url
    System.getenv("OPENAI_API_KEY"),      // apiKey
    new BigDecimal("1.25").divide(new BigDecimal(1000000)),
    new BigDecimal("0.125").divide(new BigDecimal(1000000)),
    new BigDecimal("10.0").divide(new BigDecimal(1000000)),
    BigDecimal.ZERO,
    null,
    null,
    "DYNAMIC"
);

final var client = clientService.getClient(modelProfile);
final var completion = client.conversation(modelProfile, conversation, null, false);
```

Agents can also use a specific profile directly:

```java
final var agent = new Agent(
    "MyAgent",
    AgentType.START_CONVERSATION,
    null,
    modelProfile,
    "You are a helpful assistant.",
    "{{userInput}}",
    null,
    new TextAgentResponseHandler("response"),
    null, null, null,
    AgentNext::end
);
```

### Model Profile Properties

Each profile file is a `.properties` resource on the classpath.

| Property | Type | Required | Description | Example |
|----------|------|----------|-------------|---------|
| `category` | String | No | Short profile label used in logging and graph traces. If omitted in the file, it is inferred from the `ModelCategory` used to load it. | `M`, `L`, `CUSTOM` |
| `prompts` | String | Yes | Prompt strategy name for text completions. Use an empty value for chat completions. | `llama4`, `gpt-oss`, `no`, `` |
| `tokenizer` | String | No | Tokenizer name used for counting and prompt trimming. Defaults to `jTokkit` when available, otherwise `fallback`. | `jTokkit`, `llamaServer`, `fallback` |
| `tokenEncoding` | String | Yes | Token encoding scheme used for token counting. | `cl100k_base`, `none` |
| `maxTokens` | Integer | Yes | Maximum response tokens. | `1500`, `2500` |
| `maxContextLength` | Integer | Yes | Maximum total context length. | `16000`, `32768`, `128000` |
| `temperature` | Double | Yes | Sampling temperature. Must be greater than zero. | `0.2`, `0.4`, `0.7` |
| `topP` | Double | Yes | Nucleus sampling parameter. Must be greater than zero. | `1.0`, `0.9` |
| `modelProvider` | Enum | Yes | Completion client type. | `OAI_CHAT_COMPLETION`, `OAI_TEXT_COMPLETION` |
| `modelName` | String | No | Model name or deployment. Can be empty for endpoints where the model should be auto-detected. | `gpt-4`, `Llama-3.3-70B-Instruct`, `` |
| `url` | String | Yes | Base API URL. | `https://api.openai.com/v1`, `http://localhost:5001` |
| `apiKey` | String | No | Bearer token or Spring placeholder. Can be empty for local servers. | `sk-...`, `${apikey}`, `` |
| `cost1MInTokens` | Double | Yes | Cost per 1 million input tokens. | `0.71`, `5.00`, `0` |
| `cost1MCachedInTokens` | Double | No | Cost per 1 million cached input tokens. Defaults to `cost1MInTokens`. | `0.35`, `0` |
| `cost1MOutTokens` | Double | Yes | Cost per 1 million output tokens. | `0.71`, `15.00`, `0` |
| `cost1MReasoningOutTokens` | Double | No | Cost per 1 million reasoning output tokens. Defaults to `0`. | `0`, `60.00` |
| `reasoningEffort` | String | No | Reasoning effort for reasoning-capable models. | `low`, `medium`, `high` |
| `reasoningSummaryDetails` | String | No | How reasoning content should be summarized. | `auto`, `concise`, `detailed` |

Notes:

- Missing required properties cause runtime errors.
- If `modelName` is empty, the library can resolve a model name from the remote endpoint when supported.
- Cost properties are only used for statistics and accounting.
- `cost1MCachedInTokens` defaults to the normal input token cost when not provided.
- `cost1MReasoningOutTokens` defaults to `0` when not provided.
- Reasoning properties only apply to models that expose separate reasoning output.
- `tokenEncoding` is primarily relevant for tokenizer implementations that support multiple encodings, such as `jTokkit`.

### Example Profile Files

Local chat completion:

```properties
category=M
prompts=
tokenizer=llamaServer
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

Local text completion:

```properties
category=S
prompts=gpt-oss
tokenizer=llamaServer
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

## Client Implementations

lm-lib exposes a client abstraction intended to support multiple backend implementations.

The built-in provider implementations currently support these types:

```properties
modelProvider=OAI_CHAT_COMPLETION
modelProvider=OAI_TEXT_COMPLETION
```

Chat completions are the preferred default. They are simpler to use because the library only needs to send the conversation. Text completions are still useful when you need explicit prompt formatting or must work with instruct models that depend on raw prompt structure.

Tool calling is currently supported only for chat completion models. `TextCompletionClient` does not support tools, and `supportsToolCalling()` can be used on `Client`, `ClientService`, and `BaseAgent` to detect availability before enabling tool-aware flows.

These built-in providers are OpenAI-compatible today, but that is an implementation detail of the current client set, not a hard architectural limit of the library. Additional provider integrations can be added in the library itself, and applications can plug in their own client implementations where needed.

For text completions, select a prompt strategy with the `prompts` property. lm-lib ships built-in strategies for common model families including Llama, GPT-OSS, Gemma, Mistral, Phi, Qwen, Vicuna, ChatML, Alpaca, and a no-formatting strategy.

Example:

```properties
prompts=llama3
```

Use `jTokkit` or `llamaServer` for tokenization depending on your deployment model.

## Usage

Typical integration flow:

1. Add the required dependencies.
2. Configure one or more model profiles.
3. Optionally provide a `CachedClientRepository` if requests should be cached.
4. Use `ClientService` for direct completion calls.
5. Use `AgentExecutorService` for multi-step agentic flows.

### Client Usage

```java
final var client = clientService.getClient(ModelCategory.MEDIUM);
final var completion = client.conversation(ModelCategory.MEDIUM, conversation, null, false);
```

### Tool Calling

Tool calling lets chat completion models return one or more structured tool invocations instead of a final natural-language answer. The library exposes this in both the direct client API and the agent runtime.

The main tool-calling types are:

- `ToolDefinition`: describes a callable tool and its input schema
- `ToolParameters`: helper for building nested parameter maps and JSON outputs
- `ToolCallData`: request-side tool configuration such as available tools and tool choice mode
- `ToolCall`: a tool call returned by the model

Client-side flow:

```java
final var weatherTool = new ToolDefinition(
    "get_weather",
    "Gets the current weather for a given location.",
    ToolParameters.create(Map.of("location", "City and country, for example Berlin, Germany")),
    true
);

final var trafficTool = new ToolDefinition(
    "get_traffic",
    "Gets the current traffic for a given location.",
    ToolParameters.create(Map.of("location", "City and country, for example Berlin, Germany")),
    true
);

final var toolCallData = ToolCallData.required(List.of(weatherTool, trafficTool)).withParallelToolCalls(true);
final var completion = client.conversation(ModelCategory.MEDIUM, conversation, toolCallData, false);

if (completion.finishReason() == CompletionFinishReason.TOOL_CALLS) {
    conversation.appendToolCallRound(completion, List.of(
        new Conversation.ToolResult(
            ToolParameters.create().add("forecast", "Berlin is rainy and 21C").json(),
            completion.toolCalls().getFirst().id()
        ),
        new Conversation.ToolResult(
            ToolParameters.create().add("traffic", "Berlin traffic is congested").json(),
            completion.toolCalls().get(1).id()
        )
    ));

    final var finalCompletion = client.conversation(
        ModelCategory.MEDIUM,
        conversation,
        ToolCallData.auto(List.of(weatherTool, trafficTool)),
        false
    );
}
```

`ToolCallData` helpers:

- `ToolCallData.auto(...)`: model may decide whether to call a tool
- `ToolCallData.required(...)`: model must call one or more of the provided tools
- `ToolCallData.force(...)`: model must call a specific named tool
- `toolCallData.withParallelToolCalls(true)`: asks OpenAI-compatible backends for multiple independent tool calls in one turn when the server and model support `parallel_tool_calls`

If a model returns multiple tool calls, append one assistant turn containing the returned `toolCalls()`, then append one tool turn per returned `tool_call_id` before requesting the follow-up completion.

`Conversation.appendToolCallRound(...)` wraps that handoff into one call by appending the assistant turn with the returned tool calls and then one tool turn per provided `Conversation.ToolResult`.

The returned `Completion` now exposes `toolCalls()` in addition to `response()`, `reasoning()`, `finishReason()`, and `statistics()`.

## Completions and Streaming

Both streaming and non-streaming execution paths are supported.

- Non-streaming calls return a final `Completion` object when the request finishes.
- Streaming calls accept a `Consumer<Chunk>` and still return the final `Completion` after the stream completes.
- Each `Chunk` exposes both `content()` and `reasoningContent()`.
- Final `Completion` objects expose `response()`, `reasoning()`, `finishReason()`, `statistics()`, and `toolCalls()`.

If a chat completion decides to call a tool, `finishReason()` is `CompletionFinishReason.TOOL_CALLS` and `toolCalls()` contains the requested function calls. Streaming chat completions also aggregate tool calls into the final `Completion`, including multi-tool responses when `parallel_tool_calls` is enabled and supported by the backend.

This lets you stream visible output while still capturing final structured accounting data.

## Completions Cache

If a `CachedClientRepository` bean is available, lm-lib will wrap clients with a caching layer.

Direct client calls support a `skipCache` flag:

```java
final var cached = client.conversation(ModelCategory.MEDIUM, conversation, null, false);
final var fresh = client.conversation(ModelCategory.MEDIUM, conversation, null, true);

final var streamed = client.streamConversation(ModelCategory.MEDIUM, conversation, chunk -> {
    System.out.print(chunk.content());
}, null, true);
```

`skipCache=true` bypasses reading a cached response for that request but still stores the fresh result afterwards.

For cached streaming responses, the cache layer replays the cached final result back through the chunk consumer as a single chunk.

## Completion Interceptors

Implement `CompletionInterceptor` to observe or modify requests and responses centrally.

Common use cases:

- Cost and usage accounting
- Rate limiting
- Logging and tracing
- Circuit breaking
- Request validation or policy enforcement

Interceptors receive the resolved `ModelProfile`, so they can react to model category, model cost, token limits, provider, or any other profile setting.

```java
@Component
public class MyInterceptor implements CompletionInterceptor {

    @Override
    public Conversation before(ModelProfile modelProfile, Conversation conversation) {
        LOGGER.info("Using model: {} at {}", modelProfile.modelName(), modelProfile.url());
        return conversation;
    }

    @Override
    public Completion after(ModelProfile modelProfile, Conversation conversation, Completion completion) {
        trackCosts(modelProfile, completion.statistics());
        return completion;
    }
}
```

## Graders

lm-lib includes both conventional graders and agent-based graders.

### Conventional Graders

#### Grader2

Recommended for standalone binary evaluation outside agent flows.

- Approach: multiple independent assessments with majority voting
- Trade-off: slower than a single pass, but more robust against individual grading errors
- Feedback loop: not supported

```java
final var result = Grader2.assessScoreBinary(
    "Does the text mention feature X?",
    textToGrade,
    clientService
);
```

#### Grader

Deprecated. Use `Grader2`.

### Agent Graders

#### AnswerGrader2

Recommended when you need a detailed score and explanation inside an agent flow.

- Returns a 0-100 rating plus an explanation
- Supports optional feedback loop retries when the rating is `<= 60`
- Uses `qaQuestion`, `qaAnswer`, `qaOutputDescription`, and optionally `qaFeedbackLoopDestination`

```java
context.setStringValue("qaQuestion", "Is this a good solution?");
context.setStringValue("qaAnswer", answerText);
context.setValue("qaOutputDescription", Boolean.FALSE);
context.setStringValue("qaFeedbackLoopDestination", "AnswerRefiner");
final var result = agentExecutorService.walk("AnswerGrader2", context);
```

#### AnswerGraderBinary

Recommended when speed matters more than detailed scoring.

- Returns a pass/fail evaluation
- Supports the same optional feedback loop mechanism
- Can run with or without short explanations

```java
context.setStringValue("qaQuestion", "Does this answer the question?");
context.setStringValue("qaAnswer", answerText);
context.setValue("qaOutputDescription", Boolean.FALSE);
context.setStringValue("qaFeedbackLoopDestination", "AnswerRefiner");
final var result = agentExecutorService.walk("AnswerGraderBinary", context);
```

#### AnswerGrader

Deprecated. Use `AnswerGrader2`.

### Feedback Loop Mechanism

`AnswerGrader2` and `AnswerGraderBinary` can send feedback back to another agent when evaluation fails.

Behavior:

1. The grader evaluates the answer.
2. If evaluation fails and fewer than two attempts have been made, the grader writes feedback to `qaFeedback` and routes back to `qaFeedbackLoopDestination`.
3. The source agent can revise the answer and resubmit it.
4. After the retry limit or a successful evaluation, the flow continues with the final result.

For useful feedback loops, keep `qaOutputDescription` enabled so the grader produces explanatory feedback.

## Agentic Flows

The agent runtime executes graph-based workflows over an `AgentContext`. It supports prompt templating, branching, merging, streaming, retries, grading, and execution tracing.

### Quick Start

```java
final var context = new AgentContext(List.of(new Agent1(), new Agent2()));
context.setStringValue("key", "Payload here");
context.setStringValues("key2", List.of("All context keys", "support collections", "for aggregation"));
context.setValue("key3", new RandomDatatype());
context.setSkipCache(true);
context.setAlwaysSkipCache(true);

final var resultContexts = agentExecutorService.walk("Agent1", context);
final var resultContext = resultContexts.getFirst();

LOGGER.info(
    resultContext.getStringValue("blogPost") + "\n\n" +
    resultContext.getGraph().toString() + "\n" +
    resultContext.getRequestStatistic().toString()
);
```

If you do not pass agents explicitly, the framework can autowire all agent beans from the Spring context.

### Two Ways to Implement Agents

Use either:

- `BaseAgent` for more explicit lifecycle overrides and larger agents
- `Agent` record for concise composition-based definitions

### BaseAgent Example

```java
public class BlogPostAgent implements BaseAgent {

    @Override
    public String name() {
        return "BlogPostAgent";
    }

    @Override
    public AgentType agentType(final AgentContext ctx) {
        return AgentType.START_CONVERSATION_WITH_SYSTEM_PROMPT;
    }

    @Override
    public ModelCategory modelCategory(final AgentContext ctx) {
        return ModelCategory.MEDIUM;
    }

    @Override
    public ModelProfile modelProfile(final AgentContext ctx) {
        return null;
    }

    @Override
    public String systemPrompt() {
        return "You are a concise technical blog writer.";
    }

    @Override
    public String textTemplate() {
        return "Write a blog post about {{topic}} using these bullets:\n\n{{points}}";
    }

    @Override
    public AgentResponseHandler responseHandler(final AgentContext ctx) {
        return new TextAgentResponseHandler("blogPost");
    }

    @Override
    public void preProcess(final AgentContext ctx) {
    }

    @Override
    public void postProcess(final AgentContext ctx) {
    }

    @Override
    public List<AgentContext> merge(final List<AgentContext> contexts) {
        return null;
    }

    @Override
    public AgentNext chooseNextAgent(final AgentContext ctx) {
        return AgentNext.END;
    }
}
```

### Agent Record Example

```java
final var blogPostAgent = new Agent(
    "BlogPostAgent",
    AgentType.START_CONVERSATION_WITH_SYSTEM_PROMPT,
    ModelCategory.MEDIUM,
    null,
    "You are a concise technical blog writer.",
    "Write a blog post about {{topic}} using these bullets:\n\n{{points}}",
    null,
    new TextAgentResponseHandler("blogPost"),
    null,
    null,
    null,
    ctx -> AgentNext.END
);
```

### BaseAgent Reference

| Method | Purpose | Default |
|--------|---------|---------|
| `name()` | Unique agent identifier. Required. | none |
| `agentType(context)` | Defines conversation behavior and execution mode. Required. | none |
| `modelCategory(context)` | Chooses a model category when `modelProfile()` returns `null`. | `ModelCategory.MEDIUM` |
| `modelProfile(context)` | Supplies a specific model profile and overrides `modelCategory()`. | `null` |
| `systemPrompt()` | Optional system instruction. | `null` |
| `textTemplate()` | Optional user prompt template. | `null` |
| `toolDefinitions(context)` | Optional tool definitions for tool-capable chat models. | `null` |
| `toolCallData(context)` | Optional tool request configuration. Defaults to `ToolCallData.auto(toolDefinitions)`. | `null` |
| `supportsToolCalling(context, clientService)` | Reports whether the resolved client can handle tool calls. | derived from model/provider |
| `responseHandler(context)` | Parses the model response. | `new TextAgentResponseHandler("response")` |
| `preProcess(context)` | Runs before completion. | no-op |
| `postProcess(context)` | Runs after completion. | no-op |
| `merge(contexts)` | Merges split contexts after branching. | `null` |
| `chooseNextAgent(context)` | Selects the next agent or ends the flow. Required. | none |

### Agent Tool Calling

Agents can declare tool definitions directly. For simple in-agent tool execution loops, wrap a normal response handler with `ToolCallingAgentResponseHandler`.

`ToolCallingAgentResponseHandler` takes two arguments:

- **delegate**: the response handler that runs after the tool-call loop ends and processes the final natural-language answer.
- **ToolCallHandler**: a lambda called every time the model returns one or more tool calls. It receives the current `Completion`, the `AgentContext`, and a `ToolCallResults` collector. The lambda must populate `toolCallResults` with a result for every returned tool call and then return a boolean that controls loop continuation:
  - Return `true` to append the tool results to the conversation and let the model continue with another turn.
  - Return `false` to stop the tool-call loop. The framework then passes the last completion to the delegate handler for normal response processing.

The tool-call loop is capped at six rounds. If the model returns no tool calls, `ToolCallHandler` is not invoked and the completion goes directly to the delegate.

#### Single tool call per turn (sequential)

When the model is prompted to call tools one at a time, the handler iterates the returned calls, adds a result for each, and returns `true` to trigger the next turn. It returns `false` when `toolCalls()` is empty, signalling that all tools have been called and the model can now produce its final answer.

```java
final var weatherAgent = new Agent(
    "WeatherAgent",
    AgentType.START_CONVERSATION,
    ModelCategory.MEDIUM,
    null,
    "Use the provided tool when the user asks for weather information.",
    "What is the weather in Berlin, Germany? Use the tool.",
    List.of(weatherTool),
    new ToolCallingAgentResponseHandler(
        new TextAgentResponseHandler("answer"),
        (completion, context, toolCallResults) -> {
            if (completion.toolCalls().isEmpty()) {
                return false;
            }
            toolCallResults.add(
                completion.toolCalls().getFirst(),
                ToolParameters.create().add("forecast", "Berlin is rainy and 21C")
            );
            return true;
        }
    ),
    null,
    null,
    null,
    AgentNext::end
);
```

#### Multiple tool calls per turn (parallel)

When `ToolCallData.withParallelToolCalls(true)` is configured and the model and backend support it, all tool calls may arrive in a single turn. In that case there is no need for an additional turn, so the handler adds results for every returned call and returns `false` immediately. Returning `true` would unnecessarily send the model through another tool-call round.

```java
new ToolCallingAgentResponseHandler(
    new TextAgentResponseHandler("answer"),
    (completion, context, toolCallResults) -> {
        if (completion.toolCalls().isEmpty()) {
            return false;
        }
        for (final var toolCall : completion.toolCalls()) {
            if ("get_weather".equals(toolCall.name())) {
                toolCallResults.add(toolCall, ToolParameters.create().add("forecast", "Berlin is rainy and 21C"));
            } else if ("get_traffic".equals(toolCall.name())) {
                toolCallResults.add(toolCall, ToolParameters.create().add("traffic", "Berlin traffic is congested"));
            }
        }
        // If multiple tool calls came back at once, parallel tool calling is active and
        // all results are already collected — return false to stop the loop.
        // If only one came back, the model is calling tools sequentially — return true
        // to continue and collect the remaining tools in the next turn.
        return completion.toolCalls().size() < 2;
    }
)
```

#### Delegated tool call handling

If you do not want the current agent to resolve tool calls itself, the conversation handoff now preserves assistant tool-call turns so the next `CONVERSATION` or `PROCESSING_ONLY` agent can inspect them in `preProcess()` and decide how to continue.

### Agent Types

| Type | Behavior | Typical Use |
|------|----------|-------------|
| `START_CONVERSATION` | Starts a fresh conversation without forcing a system prompt. | New thread |
| `START_CONVERSATION_WITH_SYSTEM_PROMPT` | Starts a fresh conversation with `systemPrompt()`. | Controlled resets |
| `CONVERSATION` | Continues an existing conversation. | Iterative refinement |
| `CONVERSATION_WITH_SYSTEM_PROMPT` | Continues with a dedicated system turn at index 0. | Persistent system role |
| `COMPLETION` | Stateless single-shot completion. | Transformations and one-off generation |
| `PROCESSING_ONLY` | Skips LLM calls entirely. | Pure context manipulation |

### Lifecycle

For each split, execution proceeds in this order:

1. `preProcess()`
2. Template expansion and optional branching
3. Optional LLM request unless skipped
4. Response parsing with up to two attempts
5. `postProcess()`
6. Optional merge
7. Next-agent selection

### Template Placeholders

| Placeholder | Expects | Behavior |
|-------------|---------|----------|
| `{{{key}}}` | Any JSON-serializable value | Inserts JSON verbatim |
| `{{key}}` | List | Joins values with double newlines |
| `[[key]]` | List | Branches and inserts the current element |
| `[[key|hidden]]` | List | Branches by the current element but hides it from prompt text |

Hidden placeholders are useful when you need branching keyed by internal identifiers that should not be shown to the model.

### Branching and Merging

Use `[[key]]` placeholders to branch. Each key must resolve to a list. The number of splits equals the smallest list size across the branched keys. Each split receives only its own element values for the branched keys.

Merge behavior:

- Return `null` from `merge()` to keep split contexts separate
- Return one context for many-to-one aggregation
- Return multiple contexts for custom fan-in or transformation strategies

Utility helper:

```java
BaseAgent.mergeContexts(List.of("blogPost", "ideas"), contexts)
```

### Execution Graph

Each context accumulates a graph trace that documents the path through the flow.

| Symbol | Meaning |
|--------|---------|
| `M`, `S`, `L`, `HOT`, `COLD` | Model category short name |
| agent name | Executed agent |
| `○` | Completion skipped |
| `⇉ x/n` | Branch split x of n |
| `size↣merged` | Merge size summary |
| `→` | Next agent is a `COMPLETION` agent |
| `🗨` | Next agent starts a new conversation |
| `↴` | Continue existing conversation |
| `✓`, `✗` | Grading result |
| `⚠` | Parse attempt failed and will retry |
| `☢` | Fatal error |
| `‖` | Continuation of an already-started graph |

Example:

```text
M, CodeSummarizer, ↴, M, FeatureIdentifier, →, ⇉ 1/5, M, FeatureAnalyzer, 5↣1, →, M, AnswerGenerator
```

### Response Handlers

| Handler | Purpose | Notes |
|---------|---------|-------|
| `TextAgentResponseHandler` | Stores the full response under one key | Supports streaming and reasoning aggregation into `<key>_reasoning` |
| `AccumulateTextAgentResponseHandler` | Appends each response to a list | Useful for collecting multiple variants |
| `JsonAgentResponseHandler` | Parses loose JSON and flattens values into context lists | Retries after parse failures |
| `CsvAgentResponseHandler` | Parses CSV into column-based context keys | Useful for structured tabular extraction |
| `CsvUnmarshallingAgentResponseHandler` | Parses CSV rows into typed objects | Useful when the target output maps cleanly to record-like structures |

#### TextAgentResponseHandler

Streaming behavior:

1. Initializes the target key to an empty string.
2. Appends each chunk's `content()` to the main key.
3. Appends each chunk's `reasoningContent()` to `<key>_reasoning`.
4. Runs final `handle()` after streaming ends.

#### JsonAgentResponseHandler

Behavior summary:

1. Extracts the first plausible JSON object from the response.
2. Parses it with permissive Jackson settings.
3. Flattens nested data into list-valued context keys.
4. Retries once if parsing fails.

#### CSV Handlers

The CSV handlers are useful when you want models to emit lightweight structured tabular output instead of JSON.

- `CsvAgentResponseHandler` stores parsed columns under separate context keys.
- `CsvUnmarshallingAgentResponseHandler` converts rows into typed objects and stores them under one context key.

### Streaming Agent Execution

If `context.setStreaming(true)` is enabled and the handler implements `StreamedAgentResponseHandler`, chunk updates are processed during generation. You can also pass a callback to `AgentExecutorService.walk(...)` to receive `AgentContext` updates during streaming.

### Skipping LLM Calls

Set `context.setSkipNextCompletion(true)` to turn the next step into a processing-only pass. The flag resets automatically after that step.

### Cache Bypass Controls

Agent execution supports two cache flags:

- `setSkipCache(true)` bypasses cache reads for the next prompt only and then resets
- `setAlwaysSkipCache(true)` bypasses cache reads for every later prompt until disabled

Both still allow fresh responses to be written back into the cache.

### Retry and Parse Handling

Each completion is parsed with a fresh response handler instance per attempt.

- If `handle()` returns `false` or throws, the framework retries once.
- If parsing still fails, the context is marked with `AgentErrorType.PARSING`, receives `☢`, and does not continue into merge or next-agent chaining.
- If parsing failed because of an exception, the cause is stored on the context via `getErrorException()`.

### Error Handling

Errors are isolated to the affected split.

- Runtime failures become `AgentErrorType.GENERAL`
- Final parse failures become `AgentErrorType.PARSING`
- Successful branches continue even when some branches fail
- Returned results include errored contexts after successful ones so callers can inspect failures

### AgentContext Flags and State

| Field or Flag | Purpose |
|---------------|---------|
| `conversation` | Holds the current conversation history |
| `requestStatistic` | Aggregates token, cost, and duration statistics |
| `graph` | Stores the execution trace |
| `lastCompletion` | Holds the most recent completion |
| `skipNextCompletion` | Skips the next LLM call |
| `skipCache` | Bypasses cache reads once |
| `alwaysSkipCache` | Bypasses cache reads until disabled |
| `streaming` | Enables streaming-aware execution |
| `errorType` and `errorException` | Capture execution failures |
| custom context keys | Store arbitrary values shared between agents |

### Best Practices

- Use chat completions by default and fall back to text completions only when raw prompt control matters.
- Prefer small or medium model categories for cheap filtering or classification steps.
- Escalate to larger or more expensive profiles only for synthesis or high-value reasoning steps.
- Keep response handlers deterministic and side-effect free.
- Use processing-only agents for context shaping instead of spending an LLM call.
- Use grading to gate expensive downstream work and to assert llm responses in unit tests
- Use hidden placeholders when a branch key is operationally useful but should not be shown to the model.

## Example Execution Trace

Sample output from `AgentTest#agenticFlow`:

```text
gemma3-12b-it-q5_k_m
M, CodeSummarizer, ↴, M, FeatureIdentifier, →, ⇉ 1/5, M, FeatureAnalyzer, 5↣1, →, M, AnswerGenerator
AgentRequestStatistic [requests=8, cachedPrompts=0, inTokens=27393, outTokens=4861, requestDuration=PT3M30.415881394S, getEffectiveDuration()=PT2M22.102597491S, getTps()=226.98, getOutTps()=34.21, getCost()=0]
```

This trace shows a typical pattern: summarize, identify multiple features, fan out into parallel analysis, merge results, and generate a final answer while preserving usage statistics.

---

# Example Output of Unit Test AgentTest#agenticFlow

## Announcing the New `BaseAgent` Framework: High-Performance, Context-Aware AI Orchestration

We are thrilled to introduce the new **`BaseAgent`** framework, a robust Java-based abstraction designed to streamline complex AI workflows. Built on proven patterns similar to LangChain, this framework empowers developers to create scalable, multi-path agent systems with minimal boilerplate.

At its core, `BaseAgent` provides a standardized lifecycle for interacting with LLMs, handling everything from dynamic prompt generation to sophisticated tool usage and state management. It is engineered to handle high-concurrency workloads while maintaining precise control over conversation history and execution flow.

## Key Capabilities

### 🚀 Parallel Execution with Automatic Aggregation
Stop managing multiple agent instances manually. The framework automatically decomposes a single agent context into parallel execution paths using **`Split` objects**.
*   **Concurrency:** Leverages `CompletableFuture` and `ExecutorService` to run agent variations simultaneously.
*   **Aggregation:** Automatically filters errors, sorts results, and merges successful outputs before determining the next workflow step.
*   **Visualization:** Includes built-in graph markers to visually track parallel branches and merge points during execution.

### 🌿 Conditional Prompt Branching
Ditch code duplication for multi-scenario prompts. The framework detects special placeholders (e.g., `[[key]]`) in your templates and dynamically generates distinct execution threads based on context variable values.
*   **Dynamic Paths:** Automatically creates separate agent runs for each unique value in a list (e.g., processing different user IDs or product options in parallel).
*   **Hidden Logic:** Supports "hidden" branches (`[[key|hidden]]`) to influence execution flow without exposing internal logic to the LLM.
*   **Smart Limits:** Calculates branch counts based on the smallest variable list to ensure balanced parallel execution.

### 🔁 Iterative Tool Calling with Retry Logic
Enable agents to perform complex, multi-step tasks autonomously. The framework implements a robust "Plan-Act-Observed" loop within the execution engine.
*   **Automatic Injection:** Detects tool calls, executes them, and seamlessly appends results back into the conversation history for the LLM to read in the next turn.
*   **Safety Limits:** Prevents infinite loops by capping tool call rounds (default: 4) and retry attempts for parsing failures (default: 2).
*   **Resilience:** Gracefully handles malformed responses or parsing errors, marking specific splits as failed while allowing others to succeed.

### 💬 Dynamic Conversation State Management
Maintain perfect context awareness across multi-agent chains without manual state tracking.
*   **Smart Initialization:** Automatically constructs conversation histories based on `AgentType`, handling system prompts, new starts, and continuations.
*   **Seamless Transitions:** intelligently merges system prompts into user turns or clears history when switching to completion-only agents.
*   **State Propagation:** Ensures tool call data and conversation turns are correctly preserved or cleared as the workflow moves between agents.

### 🔄 Intelligent Agent Chaining and Routing
Navigate complex workflows with automatic decision-making based on execution outcomes.
*   **Outcome-Based Routing:** The `nextAgent` logic dynamically selects the subsequent agent based on the current context and agent type (e.g., routing to a `COMPLETION` agent after a `PROCESSING_ONLY` step).
*   **Flexible Types:** Supports diverse agent roles including `START_CONVERSATION`, `CONVERSATION`, `COMPLETION`, and `PROCESSING_ONLY`.
*   **Error Containment:** Isolates execution errors within specific splits, preventing a single failure from halting the entire workflow.

Qwen3.5-9B-Q8_0
[M, CodeSummarizer, (19184 ms), ↴, M, FeatureIdentifier, (5273 ms), →, ⇉ 1/5, M, FeatureAnalyzer, (52686 ms), 5↣1, →, M, AnswerGenerator, (20392 ms)]
AgentRequestStatistic [requests=8, cachedPrompts=0, inTokens=28281, cachedInTokens=13498, outTokens=6677, reasoningOutTokens=0, requestDuration=PT4M11.98566101S, getEffectiveDuration()=PT2M32.67066665S, getTps()=228.98, getOutTps()=43.73, getCost()=0]
