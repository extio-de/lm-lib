package de.extio.lmlib.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.client.RestClient;

import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfileService;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@EnableAutoConfiguration
@PropertySource("classpath:/application-test.properties")
public class TokenizerTest {
	
	private static final String TEXT = "Hello this is a text! How are you doing? The weather is great today! Super sunny shiny!!!";
	
	@Autowired
	private ModelProfileService modelProfileService;
	
	@Autowired
	@Qualifier("lmLibRestClientBuilder")
	private RestClient.Builder restClientBuilder;
	
	@Test
	void jTokkit() throws Exception {
		final var tokenizer = new JTokkitTokenizer();
		final var profile = this.modelProfileService.getModelProfile(ModelCategory.MEDIUM.getModelProfile());
		
		assertEquals(21, tokenizer.count(TEXT, profile));
		final var tokens = tokenizer.tokenize(TEXT, profile);
		assertEquals("[9906, 420, 374, 264, 1495, 0, 2650, 527, 499, 3815, 30, 578, 9282, 374, 2294, 3432, 0, 7445, 40798, 42299, 12340]", tokens.toString());
		assertEquals(TEXT, tokenizer.detokenize(tokens, profile));
	}

	@Test
	void fallback() {
		final var tokenizer = new FallbackTokenizer();
		final var profile = this.modelProfileService.getModelProfile(ModelCategory.MEDIUM.getModelProfile());
		
		assertEquals(47, tokenizer.count(TEXT, profile));
		final var tokens = tokenizer.tokenize(TEXT, profile);
		assertEquals(47, tokens.size());
		assertEquals("[465675044868, 28417, 8193, 495689692164, 8193, 7563522, 8193, 24833, 8193, 500236121092, 8449, 8193, 2003781635, 8193, 1701994755, 8193, 1970239747, 8193, 474215310340, 26369, 16129, 8193, 1701336067, 8193, 499850245892, 1919248387, 8193, 7563522, 8193, 418313824004, 29697, 8193, 418296853508, 30977, 8449, 8193, 435678434052, 29185, 8193, 474299593476, 30977, 8193, 474214855428, 30977, 8449, 8449, 8449]", tokens.toString());
		assertEquals(TEXT, tokenizer.detokenize(tokens, profile));
	}
	
	@Test
	void fallbackTokenizerGroupsWhitespace() {
		final var tokenizer = new FallbackTokenizer();
		final var profile = this.modelProfileService.getModelProfile(ModelCategory.MEDIUM.getModelProfile());
		final var text = "       ";
		final var tokens = tokenizer.tokenize(text, profile);
		
		assertEquals(2, tokens.size());
		assertEquals(text, tokenizer.detokenize(tokens, profile));
	}
	
	/**
	 * Tested with a Llama 3.1 8B model. The test will likely fail if you load a different model.
	 */
	@Disabled("Requires llama.cpp model profile")
	@Test
	void llamaServer() throws Exception {
		final var tokenizer = new LlamaServerTokenizer(this.restClientBuilder);
		final var profile = this.modelProfileService.getModelProfile(ModelCategory.MEDIUM.getModelProfile());
		
		assertEquals(21, tokenizer.count(TEXT, profile));
		final var tokens = tokenizer.tokenize(TEXT, profile);
		assertEquals("[9906, 420, 374, 264, 1495, 0, 2650, 527, 499, 3815, 30, 578, 9282, 374, 2294, 3432, 0, 7445, 40798, 42299, 12340]", tokens.toString());
		assertEquals(TEXT, tokenizer.detokenize(tokens, profile));
	}
}
