package de.extio.lmlib.grader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.agent.AgentExecutorService;
import de.extio.lmlib.agent.BaseAgent;
import de.extio.lmlib.client.ClientService;
import de.extio.lmlib.grader.agent.AnswerGrader;
import de.extio.lmlib.grader.agent.AnswerGrader2;
import de.extio.lmlib.grader.agent.AnswerGraderBinary;
import de.extio.lmlib.profile.ModelCategory;

@Disabled("This test requires a running Llama server or a cloud subscription (setup key in model profile)")
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@ComponentScan(basePackages = "de.extio.lmlib")
@PropertySource("classpath:/application-test.properties")
@TestInstance(Lifecycle.PER_CLASS)
public class GraderTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(GraderTest.class);

	@Autowired
	private AgentExecutorService agentExecutor;

	@Autowired
	private ClientService clientService;

	@Autowired
	private de.extio.lmlib.profile.ModelProfileService modelProfileService;

	private long testStartNano;

	private final Map<String, Long> methodTotalNanos = new HashMap<>();
	private final Map<String, Integer> methodInvocationCount = new HashMap<>();
	private final Map<String, Integer> methodFailureCount = new HashMap<>();

	static Stream<Arguments> gradingScenarios() {
		return Stream.of(
				// Geography
				Arguments.of(
						"What is the capital of France?",
						"The capital of France is Paris.",
						true),
				Arguments.of(
						"What is the capital of France?",
						"I don't know.",
						false),
				Arguments.of(
						"Name three countries in Southeast Asia.",
						"Thailand, Vietnam, and Indonesia are three countries in Southeast Asia.",
						true),
				Arguments.of(
						"Name three countries in Southeast Asia.",
						"Some countries exist there.",
						false),
				// Technology Support
				Arguments.of(
						"How do I reset my password?",
						"You can reset your password by clicking on the 'Forgot Password' link on the login page and following the instructions sent to your email.",
						true),
				Arguments.of(
						"How do I reset my password?",
						"Contact support.",
						false),
				Arguments.of(
						"How do I reset my password?",
						"Read the manual!",
						false),                        
				Arguments.of(
						"How do I enable two-factor authentication?",
						"Go to Settings > Security > Two-Factor Authentication, then choose your preferred method (SMS, email, or authenticator app) and follow the setup steps.",
						true),
				Arguments.of(
						"How do I enable two-factor authentication?",
						"Ask your administrator.",
						false),
				// Science
				Arguments.of(
						"What is the boiling point of water?",
						"100 degrees Celsius at sea level.",
						true),
				Arguments.of(
						"What is the boiling point of water?",
						"It is hot.",
						false),
				Arguments.of(
						"Explain photosynthesis in one sentence.",
						"Photosynthesis is the process where plants convert sunlight, water, and carbon dioxide into glucose and oxygen.",
						true),
				Arguments.of(
						"Explain photosynthesis in one sentence.",
						"Plants do something with light.",
						false),
				// Mathematics
				Arguments.of(
						"What is 15% of 200?",
						"15% of 200 is 30.",
						true),
				Arguments.of(
						"What is 15% of 200?",
						"A lot.",
						false),
				Arguments.of(
						"Solve for x: 2x + 5 = 15",
						"x = 5, because 2(5) + 5 = 10 + 5 = 15.",
						true),
				Arguments.of(
						"Solve for x: 2x + 5 = 15",
						"Maybe x is 5 or something else.",
						false),
				Arguments.of(
						"Solve for x: 2x + 5 = 15",
						"x is 10",
						false),                        
				// History
				Arguments.of(
						"In what year did World War II end?",
						"World War II ended in 1945.",
						true),
				Arguments.of(
						"In what year did World War II end?",
						"A long time ago.",
						false),
				Arguments.of(
						"Who was the first President of the United States?",
						"George Washington was the first President of the United States, serving from 1789 to 1797.",
						true),
				Arguments.of(
						"Who was the first President of the United States?",
						"Some important person.",
						false),
				// Programming
				Arguments.of(
						"What is the time complexity of binary search?",
						"The time complexity of binary search is O(log n).",
						true),
				Arguments.of(
						"What is the time complexity of binary search?",
						"It's fast.",
						false),
				Arguments.of(
						"Explain the difference between an array and a linked list.",
						"Arrays provide constant-time access but fixed size, while linked lists provide dynamic size but require linear-time access.",
						true),
				Arguments.of(
						"Explain the difference between an array and a linked list.",
						"They are different data structures.",
						false));
	}

	@SuppressWarnings("deprecation")
	@ParameterizedTest
	@MethodSource("gradingScenarios")
	void answerGraderLegacy(final String question, final String answer, final boolean expectedPass) {
		LOGGER.info("Legacy AnswerGrader - Question: '{}' Answer: '{}'", question, answer);
		final var agent = AnswerGrader.createAgent();
		final var context = new AgentContext(Map.of("AnswerGrader", agent));
		context.getContext().put("questions", List.of(question));
		context.getContext().put("answer", List.of(answer));
		final var result = this.agentExecutor.walk(agent, context).getFirst();
		final boolean passed = (boolean) result.getContext().get("qaPassed").getFirst();
		LOGGER.info("AnswerGrader - Rating: {}, Passed: {}, Explanation: {}", result.getStringValue("qaRating"), passed, result.getStringValue("qaExplanation"));
		assertEquals(expectedPass, passed, "AnswerGrader result mismatch for question: " + question);
	}

	@ParameterizedTest
	@MethodSource("gradingScenarios")
	void answerGraderCurrent(final String question, final String answer, final boolean expectedPass) {
		final boolean passed = runAnswerGrader("AnswerGrader2", AnswerGrader2.createAgent(), context -> {
			context.getContext().put("qaQuestion", List.of(question));
			context.getContext().put("qaAnswer", List.of(answer));
		});
		assertEquals(expectedPass, passed, "AnswerGrader2 result mismatch for question: " + question);
	}

	@ParameterizedTest
	@MethodSource("gradingScenarios")
	void answerGraderBinary(final String question, final String answer, final boolean expectedPass) {
		final boolean passed = runAnswerGrader("AnswerGraderBinary", AnswerGraderBinary.createAgent(), context -> {
			context.getContext().put("qaQuestion", List.of(question));
			context.getContext().put("qaAnswer", List.of(answer));
		});
		assertEquals(expectedPass, passed, "AnswerGraderBinary result mismatch for question: " + question);
	}

	@ParameterizedTest
	@MethodSource("gradingScenarios")
	void answerGraderCurrentNoDesc(final String question, final String answer, final boolean expectedPass) {
		final boolean passed = runAnswerGrader("AnswerGrader2 (no description)", AnswerGrader2.createAgent(), context -> {
			context.getContext().put("qaQuestion", List.of(question));
			context.getContext().put("qaAnswer", List.of(answer));
			context.setValue("qaOutputDescription", Boolean.FALSE);
		});
		assertEquals(expectedPass, passed, "AnswerGrader2 (no description) result mismatch for question: " + question);
	}

	@ParameterizedTest
	@MethodSource("gradingScenarios")
	void answerGraderBinaryNoDesc(final String question, final String answer, final boolean expectedPass) {
		final boolean passed = runAnswerGrader("AnswerGraderBinary (no description)", AnswerGraderBinary.createAgent(), context -> {
			context.getContext().put("qaQuestion", List.of(question));
			context.getContext().put("qaAnswer", List.of(answer));
			context.setValue("qaOutputDescription", Boolean.FALSE);
		});
		assertEquals(expectedPass, passed, "AnswerGraderBinary (no description) result mismatch for question: " + question);
	}

	@SuppressWarnings("deprecation")
	@ParameterizedTest
	@MethodSource("gradingScenarios")
	void graderLegacy(final String question, final String answer, final boolean expectedPass) {
		final boolean result = runBinaryGrader("Grader", question, answer, (q, a) -> Grader.assessScoreBinary(q, a, this.clientService));
		assertEquals(expectedPass, result, "Grader result mismatch for question: " + question);
	}

	@ParameterizedTest
	@MethodSource("gradingScenarios")
	void graderCurrent(final String question, final String answer, final boolean expectedPass) {
		final boolean result = runBinaryGrader("Grader2", question, answer, (q, a) -> Grader2.assessScoreBinary(q, a, ModelCategory.MEDIUM, this.modelProfileService, this.clientService));
		assertEquals(expectedPass, result, "Grader2 result mismatch for question: " + question);
	}
	
	private boolean runAnswerGrader(final String label, final BaseAgent agent, final Consumer<AgentContext> contextConfigurer) {
		final var context = new AgentContext(Map.of("AnswerGrader", agent));
		contextConfigurer.accept(context);
		final var result = this.agentExecutor.walk(agent, context).getFirst();
		final boolean passed = (boolean) result.getContext().get("qaPassed").getFirst();
		LOGGER.info("{} - Rating: {}, Passed: {}, Explanation: {}", label, result.getStringValue("qaRating"), passed,
			result.getStringValue("qaExplanation"));
		return passed;
	}

	private boolean runBinaryGrader(final String label, final String question, final String answer,
		final BiFunction<String, String, Boolean> grader) {
		final boolean result = grader.apply(question, answer);
		LOGGER.info("{} - Result: {}", label, result);
		return result;
	}

	@BeforeEach
	void captureStart(TestInfo info) {
		this.testStartNano = System.nanoTime();
		LOGGER.info("Starting {}", info.getDisplayName());
	}

	@AfterEach
	void logDuration(TestInfo info) {
		final var elapsedMs = (System.nanoTime() - this.testStartNano) / 1_000_000.0;
		LOGGER.info("Completed {} in {} ms", info.getDisplayName(), String.format("%.2f", elapsedMs));
		recordDuration(info, (long) (elapsedMs * 1_000_000));
	}

	@AfterAll
	void logTotals() {
		this.methodTotalNanos.forEach((method, total) -> {
			final var count = this.methodInvocationCount.get(method);
			LOGGER.info("{} total time: {} ms over {} runs",
				method,
				String.format("%.2f", total / 1_000_000.0),
				count);
		});
		this.methodFailureCount.forEach((method, failures) -> LOGGER.info("{} failures: {}", method, failures));
	}

	private void recordDuration(final TestInfo info, final long nanos) {
		final var method = info.getTestMethod().map(Method::getName).orElse(info.getDisplayName());
		this.methodTotalNanos.merge(method, nanos, Long::sum);
		this.methodInvocationCount.merge(method, 1, Integer::sum);
	}

	@RegisterExtension
	final TestWatcher watcher = new TestWatcher() {

		private String methodName(final ExtensionContext context) {
			return context.getTestMethod().map(Method::getName).orElse(context.getDisplayName());
		}

		@Override
		public void testFailed(final ExtensionContext context, final Throwable cause) {
			GraderTest.this.methodFailureCount.merge(methodName(context), 1, Integer::sum);
		}
	};
}
