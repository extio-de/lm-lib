package de.extio.lmlib.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import de.extio.lmlib.agent.responsehandler.AgentResponseHandler;
import de.extio.lmlib.client.Client;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.CompletionStatistics;
import de.extio.lmlib.profile.ModelCategory;

class AgentErrorHandlingTest {

	@Test
	void parsingErrorsKeepTheirTypeWithoutAnExceptionWhenTheHandlerReturnsFalse() {
		final Client client = mock(Client.class);
		when(client.conversation(any(ModelCategory.class), any(), anyBoolean())).thenReturn(new Completion("not json", null, null, new CompletionStatistics(1, Duration.ofMillis(1), 1, 0, 1, 0, BigDecimal.ZERO, false)));
		
		final AgentResponseHandler responseHandler = (completion, context) -> false;
		final var agent = new Agent("ParseAgent",
				AgentType.COMPLETION,
				ModelCategory.MEDIUM,
				null,
				null,
				"prompt",
				responseHandler,
				null,
				null,
				null,
				AgentNext::end);
		final var context = new AgentContext(Map.of(agent.name(), agent));
		
		try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
			final var results = agent.execute(client, executorService, context);
			final var result = results.getFirst();
			assertTrue(result.isError());
			assertEquals(AgentErrorType.PARSING, result.getErrorType());
			assertNull(result.getErrorException());
		}
	}

	@Test
	void thrownExceptionsAreStoredAsGeneralErrors() {
		final var exception = new IllegalStateException("boom");
		final var agent = new Agent("BrokenAgent",
				AgentType.PROCESSING_ONLY,
				ModelCategory.MEDIUM,
				null,
				null,
				null,
				null,
				context -> {
					throw exception;
				},
				null,
				null,
				AgentNext::end);
		final var context = new AgentContext(Map.of(agent.name(), agent));
		
		try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
			final var results = agent.execute(null, executorService, context);
			final var result = results.getFirst();
			assertTrue(result.isError());
			assertEquals(AgentErrorType.GENERAL, result.getErrorType());
			assertInstanceOf(IllegalStateException.class, result.getErrorException());
			assertEquals("boom", result.getErrorException().getMessage());
		}
	}
}