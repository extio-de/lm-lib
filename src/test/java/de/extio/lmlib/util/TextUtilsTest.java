package de.extio.lmlib.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class TextUtilsTest {
	
	@Test
	void normalizeResponseTestRemovePremable() {
		final String textToNormalize = """
				Here is a clear and concise answer to the support question:
				
				The `enrichFuelSurcharchesCronEndpoint` Camel route uses the `FuelSurchargeEnricher` bean, which is responsible for enriching transport data with fuel surcharge information. This bean performs the following tasks:
				
				* Calculates the fuel surcharge per mile based on the current fuel rate and transport distance
				* Adds the calculated surcharge to the transport's price additional 1
				* Logs information about the enrichment process
				
				The `FuelSurchargeEnricher` bean relies on other services, including `DataServiceFacade` for database data retrieval, `EiaFuelRatesApi` for current fuel rate data, and `ExchangeRouteLogger` for logging purposes. If you require further information or clarification on the implementation details, please contact the Enterprise Integration team.""";
		
		final String expectedResponse = """
				The `enrichFuelSurcharchesCronEndpoint` Camel route uses the `FuelSurchargeEnricher` bean, which is responsible for enriching transport data with fuel surcharge information. This bean performs the following tasks:
				
				* Calculates the fuel surcharge per mile based on the current fuel rate and transport distance
				* Adds the calculated surcharge to the transport's price additional 1
				* Logs information about the enrichment process
				
				The `FuelSurchargeEnricher` bean relies on other services, including `DataServiceFacade` for database data retrieval, `EiaFuelRatesApi` for current fuel rate data, and `ExchangeRouteLogger` for logging purposes. If you require further information or clarification on the implementation details, please contact the Enterprise Integration team.""";
		
		final String result = TextUtils.normalizeModelResponse(textToNormalize, true);
		assertEquals(expectedResponse, result);
	}
	
	@Test
	void normalizeResponseTestDontRemoveFirstLine1() {
		final String textToNormalize = """
				Camel Route Flow Description
				
				#### Route Name: `bookingModified`
				
				1. **Initiation**: The `bookingModified` route starts when a message is sent to the `direct:bookingModified` endpoint.
				2. **Setting Headers**: The route sets the following headers:
				   - `TpExchangeDirection` to `IN`
				   - `TpExchangeDescription` to `Booking modified`
				   - `TpLogBody` to `true`
				3. **Filtering**: It calls the `isChangeRelevant` method on the `exportChecker` bean with the current and original booking as parameters. If the method returns `true`, the route proceeds.
				4. **Routing to `outboundIDOCEXP`**: If the filter condition is met, the message is sent to the `direct:outboundIDOCEXP` endpoint, initiating the `outboundIDOCEXP` route.
				
				#### Route Name: `outboundIDOCEXP`
				
				1. **Initiation**: This route starts when a message is sent to the `direct:outboundIDOCEXP` endpoint.
				2. **Setting Headers**: The route sets several headers:
				   - `TpExchangeDirection` to `OUT`
				   - `TpExchangeDescription` to a concatenated string containing the original description and `| IDOC ZTRP_EXP export`
				   - `TpLogBody` to `true`
				   - `CamelFileName` to a generated filename based on the current timestamp (`ZTRP_EXP_<timestamp>.txt`)
				3. **Determining Environment**: It checks the server environment using the `serverEnvironmentProvider` bean and sets the `_TpEnvironment` header:
				   - Productive environment: `_TpEnvironment` is set to `SAPPRD`.
				   - Customer testing environment: `_TpEnvironment` is set to `SAPQUA`.
				4. **Setting `TpTransportNumber` Header**: The `TpTransportNumber` header is set to the `transportNumber` property of the current booking.
				5. **Processing Dispatch Status**: The route calls the `process` method on the `dispatchStatusProvider` bean to extract dispatch status information from the booking and sets it as a header (`_TpStatusTimes`).
				6. **Templating**: It uses the `velocity` component with the `aquamarkleclerc-export-IDOC-EXP.vm` template to generate an output message.
				7. **Sending to Outbound Endpoint**: The generated output message is sent to the `outboundEndpoint`, configured to send data to the customer's system.
				
				### Summary of the Flow
				
				- The flow starts with the `bookingModified` route.
				- It checks if the change is relevant and proceeds if true.
				- It then initiates the `outboundIDOCEXP` route.
				- The `outboundIDOCEXP` route processes the message, sets necessary headers, and generates an output message based on a template.
				- The final output message, containing data extracted from the booking and dispatch status, is sent to the customer's system via the `outboundEndpoint`.
				
				This description outlines the steps from the initiation of the `bookingModified` route to the end of the `outboundIDOCEXP` route, detailing the processing and transformations that occur along the way.""";
		
		final String expectedResponse = """
				Camel Route Flow Description
				
				#### Route Name: `bookingModified`
				
				1. **Initiation**: The `bookingModified` route starts when a message is sent to the `direct:bookingModified` endpoint.
				2. **Setting Headers**: The route sets the following headers:
				   - `TpExchangeDirection` to `IN`
				   - `TpExchangeDescription` to `Booking modified`
				   - `TpLogBody` to `true`
				3. **Filtering**: It calls the `isChangeRelevant` method on the `exportChecker` bean with the current and original booking as parameters. If the method returns `true`, the route proceeds.
				4. **Routing to `outboundIDOCEXP`**: If the filter condition is met, the message is sent to the `direct:outboundIDOCEXP` endpoint, initiating the `outboundIDOCEXP` route.
				
				#### Route Name: `outboundIDOCEXP`
				
				1. **Initiation**: This route starts when a message is sent to the `direct:outboundIDOCEXP` endpoint.
				2. **Setting Headers**: The route sets several headers:
				   - `TpExchangeDirection` to `OUT`
				   - `TpExchangeDescription` to a concatenated string containing the original description and `| IDOC ZTRP_EXP export`
				   - `TpLogBody` to `true`
				   - `CamelFileName` to a generated filename based on the current timestamp (`ZTRP_EXP_<timestamp>.txt`)
				3. **Determining Environment**: It checks the server environment using the `serverEnvironmentProvider` bean and sets the `_TpEnvironment` header:
				   - Productive environment: `_TpEnvironment` is set to `SAPPRD`.
				   - Customer testing environment: `_TpEnvironment` is set to `SAPQUA`.
				4. **Setting `TpTransportNumber` Header**: The `TpTransportNumber` header is set to the `transportNumber` property of the current booking.
				5. **Processing Dispatch Status**: The route calls the `process` method on the `dispatchStatusProvider` bean to extract dispatch status information from the booking and sets it as a header (`_TpStatusTimes`).
				6. **Templating**: It uses the `velocity` component with the `aquamarkleclerc-export-IDOC-EXP.vm` template to generate an output message.
				7. **Sending to Outbound Endpoint**: The generated output message is sent to the `outboundEndpoint`, configured to send data to the customer's system.
				
				### Summary of the Flow
				
				- The flow starts with the `bookingModified` route.
				- It checks if the change is relevant and proceeds if true.
				- It then initiates the `outboundIDOCEXP` route.
				- The `outboundIDOCEXP` route processes the message, sets necessary headers, and generates an output message based on a template.
				- The final output message, containing data extracted from the booking and dispatch status, is sent to the customer's system via the `outboundEndpoint`.
				
				This description outlines the steps from the initiation of the `bookingModified` route to the end of the `outboundIDOCEXP` route, detailing the processing and transformations that occur along the way.""";
		
		final String result = TextUtils.normalizeModelResponse(textToNormalize, true);
		assertEquals(expectedResponse, result);
	}
	
	@Test
	void normalizeResponseTestDontRemoveFirstLine2() {
		final String textToNormalize = """
				The output is an XML document that represents a transport assignment data in a specific format. It contains a `tisys` root element with a `Transport2Inhouse` child element, which includes details such as:
				
				- `transport_number`
				- `status` (if deleted)
				- `assigned_carrier`
				- `price`
				- `currency`
				
				The XML structure and values are determined based on the properties of the shipment data, specifically the `assignmentMode` and `deleted` status.
				
				The resulting XML output will be sent to the `outboundEndpoint` as part of the Apache Camel route.""";
		
		final String expectedResponse = """
				The output is an XML document that represents a transport assignment data in a specific format. It contains a `tisys` root element with a `Transport2Inhouse` child element, which includes details such as:
				
				- `transport_number`
				- `status` (if deleted)
				- `assigned_carrier`
				- `price`
				- `currency`
				
				The XML structure and values are determined based on the properties of the shipment data, specifically the `assignmentMode` and `deleted` status.
				
				The resulting XML output will be sent to the `outboundEndpoint` as part of the Apache Camel route.""";
		
		final String result = TextUtils.normalizeModelResponse(textToNormalize, true);
		assertEquals(expectedResponse, result);
	}
	
	@Test
	void normalizeResponseTestStripQuotesWithPreamble() {
		final String textToNormalize = """
				**Here is what he said:**
				
				This is coming directly from the source.""";
		
		final String expectedResponse = "This is coming directly from the source.";
		
		final String result = TextUtils.normalizeModelResponse(textToNormalize, true);
		assertEquals(expectedResponse, result);
	}

	@Test
	void normalizeResponseTestStripQuotesNoPreamble() {
		final String textToNormalize = "«This is a quote directly from the source.»";
		final String expectedResponse = "This is a quote directly from the source.";
		
		final String result = TextUtils.normalizeModelResponse(textToNormalize, false);
		assertEquals(expectedResponse, result);
	}

	@Test
	void splitParagraphsTest() {
		final List<String> splittedText = TextUtils.splitParagraphs(generateText(), 1750, 200, false);
		
		assertEquals(3, splittedText.size());
		for (final String chunk : splittedText) {
			assertTrue(chunk.length() <= 1950);
		}
	}
	
	@Test
	void matchWholeWordTest() {
		assertTrue(TextUtils.matchWholeWordCaseInsensitive("The quick brown fox jumps over the lazy dog", "fox"));
		assertTrue(TextUtils.matchWholeWordCaseInsensitive("The quick brown fox jumps over the lazy dog", "Fox"));
		assertTrue(TextUtils.matchWholeWordCaseInsensitive("The quick brown fox jumps over the lazy dog", "jumps"));
		assertFalse(TextUtils.matchWholeWordCaseInsensitive("The quick brown fox jumps over the lazy dog", "foxy"));
		assertFalse(TextUtils.matchWholeWordCaseInsensitive("The quick brown fox jumps over the lazy dog", "jump"));
		assertFalse(TextUtils.matchWholeWordCaseInsensitive("The quick brown fox jumps over the lazy dog", "row"));
		
		assertTrue(TextUtils.matchWholeWordCaseInsensitive("RandomHelloService.doItNow()", "doItNow"));
		assertTrue(TextUtils.matchWholeWordCaseInsensitive("RandomHelloService.doItNow()", "RandomHelloService"));
		assertFalse(TextUtils.matchWholeWordCaseInsensitive("RandomHelloService.doItNow()", "HelloService"));
		assertFalse(TextUtils.matchWholeWordCaseInsensitive("RandomHelloService.doItNow()", "RandomHello"));
		assertFalse(TextUtils.matchWholeWordCaseInsensitive("RandomHelloService.doItNow()", "doIt"));
		assertFalse(TextUtils.matchWholeWordCaseInsensitive("RandomHelloService.doItNow()", "now"));
	}
	
	@ParameterizedTest
	@CsvSource({
			"'This is a test with special characters', 'test', true",
			"'This is a test with special characters', 'test*', false",
			"'This is a test with special characters', 'test+', false",
			"'This is a test with special characters', 'test?', false",
			"'This is a test with special characters', 'test$', false",
			"'This is a test with special characters', 'test${exchange}', false",
			"'This is a test with special characters', 'test{2}', false",
			"'This is a test with special characters', 'test[abc]', false",
			
			"'Service.process(${param}, [1,2,3])', 'Service', true",
			"'Service.process(${param}, [1,2,3])', 'process', true",
			"'Service.process(${param}, [1,2,3])', 'param', true",
			
			"'test${variable}test', 'test', true",
			"'test(test)test', 'test', true",
			"'test[array]test', 'test', true",
			"'test*star+plus?question', 'test', true",
			"'test^caret$dollar|pipe', 'test', true"
	})
	void matchWholeWordWithRegexSpecialCharactersTest(final String text, final String word, final boolean expectedResult) {
		try {
			final boolean result = TextUtils.matchWholeWordCaseInsensitive(text, word);
			assertEquals(expectedResult, result);
		}
		catch (final Exception e) {
			fail(String.format("Exception thrown for '%s' in '%s': %s", word, text, e.getMessage()));
		}
	}
	
	@ParameterizedTest
	@CsvSource({
			"'', '', 0",
			"'abc', '', 3",
			"'', 'abc', 3",
			"'abc', 'abc', 0",
			"'abc', 'def', 3",
			"'cursed_sword', 'cursed_blade', 5",
			"'main_quest', 'main_quests', 1",
			"'betrayal', 'betrayel', 1",
			"'dragon', 'wagon', 2",
			"'kitten', 'sitting', 3",
			"'saturday', 'sunday', 3",
			"'prophecy_fulfilled', 'prophecy_fullfilled', 1",
			"'dark_forest', 'deep_forest', 3"
	})
	void levenshteinDistanceTest(final String s1, final String s2, final int expectedDistance) {
		final int distance = TextUtils.levenshteinDistance(s1, s2);
		assertEquals(expectedDistance, distance);
	}
	
	@ParameterizedTest
	@CsvSource({
			"'', '', 1.0",
			"'abc', 'abc', 1.0",
			"'abc', 'def', 0.0",
			"'cursed_sword', 'cursed_blade', 0.5833333333333334",
			"'main_quest', 'main_quests', 0.9",
			"'betrayal', 'betrayel', 0.875",
			"'dragon', 'wagon', 0.6666666666666667",
			"'kitten', 'sitting', 0.5714285714285714",
			"'saturday', 'sunday', 0.625",
			"'prophecy_fulfilled', 'prophecy_fullfilled', 0.9444444444444444",
			"'dark_forest', 'deep_forest', 0.72",
			"'quest', 'quests', 0.8333333333333334",
			"'npc_merchant', 'npc_merchent', 0.9166666666666666"
	})
	void levenshteinSimilarityTest(final String s1, final String s2, final double expectedSimilarity) {
		final double similarity = TextUtils.levenshteinSimilarity(s1, s2);
		assertEquals(expectedSimilarity, similarity, 0.01);
	}
	
	@ParameterizedTest
	@CsvSource({
			"'main_quest', 'main_quests', 0.85, true",
			"'betrayal', 'betrayel', 0.85, true",
			"'dragon', 'wagon', 0.85, false",
			"'cursed_sword', 'cursed_blade', 0.85, false",
			"'prophecy_fulfilled', 'prophecy_fullfilled', 0.85, true",
			"'dark_forest', 'deep_forest', 0.85, false",
			"'quest', 'quests', 0.80, true",
			"'npc_merchant', 'npc_merchent', 0.90, true"
	})
	void levenshteinSimilarityThresholdTest(final String s1, final String s2, final double threshold, final boolean expectedMatch) {
		final double similarity = TextUtils.levenshteinSimilarity(s1, s2);
		final boolean matches = similarity >= threshold;
		assertEquals(expectedMatch, matches, 
				String.format("'%s' vs '%s' = %.2f (threshold %.2f)", s1, s2, similarity, threshold));
	}
	
	@Test
	void levenshteinSimilarityBenchmarkTest() {
		final String[][] testPairs = {
				{"cursed_sword", "cursed_blade"},
				{"main_quest", "main_quests"},
				{"betrayal_warning", "betrayel_warning"},
				{"ancient_prophecy_fulfilled", "ancient_prophecy_fullfilled"},
				{"dark_forest_encounter", "deep_forest_encounter"},
				{"npc_merchant_guild", "npc_merchent_guild"},
				{"dragon_slayer_quest", "dragon_slayer_quests"},
				{"mysterious_artifact", "mysterious_artefact"}
		};
		
		final int warmupIterations = 250000;
		final int benchmarkIterations = 250000;
		
		for (final String[] pair : testPairs) {
			for (int i = 0; i < warmupIterations; i++) {
				TextUtils.levenshteinSimilarity(pair[0], pair[1]);
			}
		}
		
		final long startTime = System.nanoTime();
		for (int i = 0; i < benchmarkIterations; i++) {
			for (final String[] pair : testPairs) {
				TextUtils.levenshteinSimilarity(pair[0], pair[1]);
			}
		}
		final long endTime = System.nanoTime();
		
		final long totalComparisons = (long) benchmarkIterations * testPairs.length;
		final long totalTimeNanos = endTime - startTime;
		final double avgTimeNanos = (double) totalTimeNanos / totalComparisons;
		final double avgTimeMicros = avgTimeNanos / 1000.0;
		
		System.out.printf("Levenshtein similarity benchmark:%n");
		System.out.printf("  Total comparisons: %,d%n", totalComparisons);
		System.out.printf("  Total time: %.2f ms%n", totalTimeNanos / 1_000_000.0);
		System.out.printf("  Average time per comparison: %.3f µs (%.0f ns)%n", avgTimeMicros, avgTimeNanos);
		System.out.printf("  Throughput: %,.0f comparisons/second%n", 1_000_000_000.0 / avgTimeNanos);
	}
	
	private static String generateText() {
		return "The *flow* of the Camel `route` named `outboundConfirmationMessage` from `start` to finish is as `follows`:\r\n"
				+ "1. The `route` starts with a from *endpoint* that *listens* on a direct *endpoint* named `direct:outboundConfirmationMessage`.\r\n"
				+ "2. The `setHeader` processor sets the `TpExchangeLogReference` header.\r\n"
				+ "3. The `first` filter `processor` checks if the *isTPM* method of the `eventProducerFilter` bean returns *true*. If it does, the *route* *continues*. Otherwise, it *stops*.\r\n"
				+ "4. If the first filter `passes`, the `setHeader` processor sets the `TpLogMessage` header.\r\n"
				+ "5. The *second* filter *processor* checks if the *creationMode* of the message body is equal to 'AUTOMATIC'. If it is, the *route* *continues*. Otherwise, it *stops*.\r\n"
				+ "6. If the *second* filter passes, the bean processor calls the **forOutboundConfirmationMessage** method of the **outboundTransmissionFactory** bean, *passing* in the transport object, the *eventQualifier* header, and the exchange object.\r\n"
				+ "7. The bean processor calls the send method of the *outboundTransmissionServiceClient* bean, `passing` in the *transmission* object and the *exchange* object.\r\n"
				+ "8. The *outboundTransmissionServiceClient* bean sends the *transmission* to the **Webservice** client using the publish *method*.\r\n"
				+ "9. The **Webservice** client returns a **PublishResponse** object, which is *logged* by the `ExchangeRouteLogger` bean.\r\n"
				+ "Note *that* the route *stops* if either of the *filters* returns false.\r\n"
				+ "The *flow* of the Camel `route` named `outboundConfirmationMessage` from `start` to finish is as `follows`:\r\n"
				+ "1. The `route` starts with a from *endpoint* that *listens* on a direct *endpoint* named `direct:outboundConfirmationMessage`.\r\n"
				+ "2. The `setHeader` processor sets the `TpExchangeLogReference` header.\r\n"
				+ "3. The `first` filter `processor` checks if the *isTPM* method of the `eventProducerFilter` bean returns *true*. If it does, the *route* *continues*. Otherwise, it *stops*.\r\n"
				+ "4. If the first filter `passes`, the `setHeader` processor sets the `TpLogMessage` header.\r\n"
				+ "5. The *second* filter *processor* checks if the *creationMode* of the message body is equal to 'AUTOMATIC'. If it is, the *route* *continues*. Otherwise, it *stops*.\r\n"
				+ "6. If the *second* filter passes, the bean processor calls the **forOutboundConfirmationMessage** method of the **outboundTransmissionFactory** bean, *passing* in the transport object, the *eventQualifier* header, and the exchange object.\r\n"
				+ "7. The bean processor calls the send method of the *outboundTransmissionServiceClient* bean, `passing` in the *transmission* object and the *exchange* object.\r\n"
				+ "8. The *outboundTransmissionServiceClient* bean sends the *transmission* to the **Webservice** client using the publish *method*.\r\n"
				+ "9. The **Webservice** client returns a **PublishResponse** object, which is *logged* by the `ExchangeRouteLogger` bean.\r\n"
				+ "Note *that* the route *stops* if either of the *filters* returns false.\r\n"
				+ "The *flow* of the Camel `route` named `outboundConfirmationMessage` from `start` to finish is as `follows`:\r\n"
				+ "1. The `route` starts with a from *endpoint* that *listens* on a direct *endpoint* named `direct:outboundConfirmationMessage`.\r\n"
				+ "2. The `setHeader` processor sets the `TpExchangeLogReference` header.\r\n"
				+ "3. The `first` filter `processor` checks if the *isTPM* method of the `eventProducerFilter` bean returns *true*. If it does, the *route* *continues*. Otherwise, it *stops*.\r\n"
				+ "4. If the first filter `passes`, the `setHeader` processor sets the `TpLogMessage` header.\r\n"
				+ "5. The *second* filter *processor* checks if the *creationMode* of the message body is equal to 'AUTOMATIC'. If it is, the *route* *continues*. Otherwise, it *stops*.\r\n"
				+ "6. If the *second* filter passes, the bean processor calls the **forOutboundConfirmationMessage** method of the **outboundTransmissionFactory** bean, *passing* in the transport object, the *eventQualifier* header, and the exchange object.\r\n"
				+ "7. The bean processor calls the send method of the *outboundTransmissionServiceClient* bean, `passing` in the *transmission* object and the *exchange* object.\r\n"
				+ "8. The *outboundTransmissionServiceClient* bean sends the *transmission* to the **Webservice** client using the publish *method*.\r\n"
				+ "9. The **Webservice** client returns a **PublishResponse** object, which is *logged* by the `ExchangeRouteLogger` bean.\r\n"
				+ "Note *that* the route *stops* if either of the *filters* returns false.";
	}
}
