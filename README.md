# Purpose

The purpose of this application is to provide a comprehensive conversational AI system that enables efficient and effective human-computer interactions through natural language processing, conversation management, and text completion capabilities, utilizing caching, tokenization, and integration with external AI models and services to deliver a robust and scalable solution for applications such as chatbots, virtual assistants, and customer service platforms.

# Program Description

The program is a comprehensive conversational AI system that provides natural language processing functionality, conversation management, and text completion capabilities. The purpose of the program is to enable efficient and effective human-computer interactions through AI-powered conversations.

The program consists of several packages that work together to achieve this goal. These packages include:

1. **Caching layer (com.transporeon.exchange.lmlib.client.cached)**: Improves performance by reducing requests to the underlying client through caching and retrieving completion responses.
2. **Natural Language Processing (com.transporeon.exchange.lmlib.client)**: Provides conversation management and text completion functionality, generating completions, processing conversations, and managing models.
3. **Azure AI Integration (com.transporeon.exchange.lmlib.client.azureai)**: Integrates with Azure AI services, managing model profiles and handling conversation completions.
4. **Text Completion (com.transporeon.exchange.lmlib.client.textcompletion)**: Provides text completion functionality using AI models, including model selection, prompt generation, and response handling.
5. **Text Tokenization (com.transporeon.exchange.lmlib.token)**: Tokenizes text into numerical tokens and detokenizes numerical tokens back into human-readable text.
6. **Conversation Prompts (com.transporeon.exchange.lmlib.prompt)**: Manages conversation prompts, creating new prompts, continuing conversations, and removing end-of-text markers.
7. **Agent Executor (com.transporeon.exchange.lmlib.agent)**: Executes agent graphs concurrently, handles agent responses, and tracks request statistics.

The program's key features include:

* Agent graph execution
* Conversation management and text completion
* Integration with external AI models and services (e.g., Azure AI)
* Support for multiple prompt strategies and tokenization implementations
* Caching and performance optimization

Overall, the program is designed to provide a robust and efficient conversational AI system that can be used in various applications, such as chatbots, virtual assistants, and customer service platforms.

*AgentRequestStatistic [requests=43, cachedPrompts=2, inTokens=23437, outTokens=7403, requestDuration=PT2M31.7349284S, getEffectiveDuration()=PT1M37.2400495S, getTps()=317.15, getOutTps()=76.13, getCost()=0.01458674]*

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

Profiles must be located in the main resources location.

Examples can be found in test resources.

## OpenAi compatible text completion client

Use tokenizer strategy jtokkit or llamaServer (if the model is hosted on a llama.cpp server)

Property:

    tokenizer.strategy=llamaServer

If you use jtokkit tokenizer:

    <dependency>
        <groupId>com.knuddels</groupId>
        <artifactId>jtokkit</artifactId>
    </dependency>

It is required to select the right prompt template. See package com.transporeon.exchange.lmlib.prompt for available prompt templates or implement your own PromptStrategy (must be a bean in the spring context).

Set the following property in your model profile:

    prompts=llama3

Example profile: src/test/resources/llama3-local.properties

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

# Completions Cache

All language model requests will be cached if you add a CachedClientRepository bean to your context.

# Agentic Flows

This library contains a graph executor for implementing comprehensive processes using agent-based design patterns. See class AgentTest for examples.

# Usage

1. Setup dependencies, see above
2. Configure model profiles, see above
3. Implement a CachedClientRepository if you need your requests cached
4. For chats or completions, use ClientService service bean
5. For agentic flows, create an AgentContext and use AgentExecutorService service bean

# Examples

Text completions: AzureAiClientTest#completion  
Chat: AzureAiClientTest#chat  
Agentic flow: AgentTest#agenticFlow  

# Announcement generated by AgentTest#agenticFlow

Title: Introducing a Powerful Framework for Building Intelligent Agents

We're excited to announce the development of a new Java codebase designed to build intelligent agents capable of interacting with language models like GPT. This framework offers a flexible and extensible architecture, empowering developers to create sophisticated conversational agents for a wide range of applications.

**Key Features:**

* **Concurrent Text Processing:**  Leveraging Java's `CompletableFuture` and `ExecutorService`, the framework enables parallel processing of text chunks, significantly speeding up agent execution.

* **Response-Driven Agent Selection:** The `chooseNextAgent` method intelligently selects the next agent in the chain based on the response received from the language model, allowing for dynamic and adaptive conversations.

* **Customizable Response Handling:** Each agent can be equipped with a custom `responseHandler` to parse and interpret responses from the language model, enabling tailored logic for specific tasks.

* **Dynamic Agent Chaining:** Agents can be chained together to create complex, multi-step interactions, with each agent specializing in a particular task and passing context to the next.

* **Contextualized Agent Execution:** The framework utilizes an `AgentContext` object to store and share information between agents, ensuring each agent has access to the relevant context for its task.

**How it Works:**

The framework revolves around the `Agent` class, which defines an agent's behavior and interaction with the language model. Each agent receives an `AgentContext` containing relevant information, processes text using its defined template, interacts with the language model, and then uses its `responseHandler` to interpret the response. Based on the response, the `chooseNextAgent` method determines the next agent in the chain.

**Benefits:**

This framework offers several advantages for developers:

* **Flexibility:** Easily customize agent behavior through configurable parameters and custom response handlers.
* **Extensibility:** Add new agent types and functionalities as needed.
* **Efficiency:** Parallel processing and dynamic agent chaining optimize performance.
* **Adaptability:** Agents can respond intelligently to changing conversation contexts.

**Looking Ahead:**

We believe this framework has the potential to revolutionize the way we build conversational agents. We're actively developing new features and functionalities, and we encourage the community to contribute and explore the possibilities.

Stay tuned for more updates and documentation!

*Graph: M, CodeSummarizer, ↴, M, FeatureIdentifier, →, ⇉ 3/5, M, FeatureAnalyzer, →, 5↣1, M, AnswerGenerator*
*gemma2-27b requests=8, inTokens=19646, outTokens=2784, requestDuration=PT7M38.309006603S, getAbsoluteDuration()=PT3M54.653718763S, getTps()=95.59, getOutTps()=11.86*
