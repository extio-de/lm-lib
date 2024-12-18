package de.extio.lmlib.token;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfileService;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@ComponentScan(basePackages = "de.extio.lmlib")
@PropertySource("classpath:/application-test.properties")
public class TokenizerTest {
	
	private static final String TEXT = "Hello this is a text! How are you doing? The weather is great today! Super sunny shiny!!!";
	
	@Autowired
	private ModelProfileService modelProfileService;
	
	@Autowired
	@Qualifier("lmLibWebClientBuilder")
	private WebClient.Builder webClientBuilder;
	
	@Test
	void jTokkit() throws Exception {
		final var tokenizer = new JTokkitTokenizer();
		final var profile = this.modelProfileService.getModelProfile(ModelCategory.MEDIUM.getModelProfile());
		
		assertEquals(21, tokenizer.count(TEXT, profile));
		final var tokens = tokenizer.tokenize(TEXT, profile);
		assertEquals("[9906, 420, 374, 264, 1495, 0, 2650, 527, 499, 3815, 30, 578, 9282, 374, 2294, 3432, 0, 7445, 40798, 42299, 12340]", tokens.toString());
		assertEquals(TEXT, tokenizer.detokenize(tokens, profile));
	}
	
	/**
	 * Tested with a Llama 3.1 8B model. The test will likely fail if you load a different model. 
	 */
	@Test
	void llamaServer() throws Exception {
		final var tokenizer = new LlamaServerTokenizer(this.webClientBuilder);
		final var profile = this.modelProfileService.getModelProfile(ModelCategory.MEDIUM.getModelProfile());
		
		assertEquals(21, tokenizer.count(TEXT, profile));
		final var tokens = tokenizer.tokenize(TEXT, profile);
		assertEquals("[9906, 420, 374, 264, 1495, 0, 2650, 527, 499, 3815, 30, 578, 9282, 374, 2294, 3432, 0, 7445, 40798, 42299, 12340]", tokens.toString());
		assertEquals(TEXT, tokenizer.detokenize(tokens, profile));
	}
}
