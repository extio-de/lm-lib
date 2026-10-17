package de.extio.lmlib.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import de.extio.lmlib.client.ProxyAuthorizationSupport;
import de.extio.lmlib.profile.ModelCategory;
import de.extio.lmlib.profile.ModelProfileService;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@SpringBootConfiguration
@EnableAutoConfiguration
@TestPropertySource("classpath:/application-test.properties")
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
		
		assertEquals(22, tokenizer.count(TEXT, profile));
		final var tokens = tokenizer.tokenize(TEXT, profile);
		assertEquals(22, tokens.size());
		assertEquals("[122511465728005, 126896561201157, 1936269315, 6365186, 128060447006725, 8449, 512968105988, 435710664708, 504381382660, 29113321720651782, 16129, 435542040580, 7307218357071585287, 29185, 1936269315, 32758185637388294, 34165556176494598, 8449, 32199681024139270, 34179892877926406, 34179871184986118, 555819267]", tokens.toString());
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
		final var tokenizer = new LlamaServerTokenizer(this.restClientBuilder, new ProxyAuthorizationSupport(ProxyAuthorizationSupport.MODE_HTTP_CLIENT, "", ""));
		final var profile = this.modelProfileService.getModelProfile(ModelCategory.MEDIUM.getModelProfile());
		
		assertEquals(21, tokenizer.count(TEXT, profile));
		final var tokens = tokenizer.tokenize(TEXT, profile);
		assertEquals("[9906, 420, 374, 264, 1495, 0, 2650, 527, 499, 3815, 30, 578, 9282, 374, 2294, 3432, 0, 7445, 40798, 42299, 12340]", tokens.toString());
		assertEquals(TEXT, tokenizer.detokenize(tokens, profile));
	}
	
	@Test
	void anthropicCodepointRoundTrip() {
		final var tokenizer = new AnthropicTokenizer(this.restClientBuilder, proxyAuthorizationSupport());
		final var profile = this.modelProfileService.getModelProfile("profile.model.anthropic");
		
		final var tokens = tokenizer.tokenize(TEXT, profile);
		assertEquals(TEXT.codePoints().count(), tokens.size());
		assertEquals(TEXT, tokenizer.detokenize(tokens, profile));
		assertEquals(0, tokenizer.count("", profile));
		assertEquals(0, tokenizer.tokenize("", profile).size());
		assertEquals("", tokenizer.detokenize(List.of(), profile));
	}
	
	@Test
	void anthropicCountTokensPath() {
		assertEquals("/v1/messages/count_tokens", AnthropicTokenizer.countTokensPath("http://127.0.0.1:5001"));
		assertEquals("/messages/count_tokens", AnthropicTokenizer.countTokensPath("http://127.0.0.1:5001/v1"));
		assertEquals("/messages/count_tokens", AnthropicTokenizer.countTokensPath("http://127.0.0.1:5001/v1/"));
		assertEquals("/count_tokens", AnthropicTokenizer.countTokensPath("http://127.0.0.1:5001/v1/messages"));
		assertEquals("http://127.0.0.1:5001", AnthropicTokenizer.stripCountTokensSuffix("http://127.0.0.1:5001/v1/messages"));
	}
	
	@Disabled("This test requires a running Anthropic server")
	@Test
	void anthropic() throws Exception {
		final var tokenizer = new AnthropicTokenizer(this.restClientBuilder, proxyAuthorizationSupport());
		final var profile = this.modelProfileService.getModelProfile("profile.model.anthropic");
		
		assertTrue(tokenizer.count(TEXT, profile) > 0);
	}
	
	private static ProxyAuthorizationSupport proxyAuthorizationSupport() {
		return new ProxyAuthorizationSupport(ProxyAuthorizationSupport.MODE_HTTP_CLIENT, "", "");
	}
}
