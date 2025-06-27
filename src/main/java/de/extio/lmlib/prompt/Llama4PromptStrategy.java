package de.extio.lmlib.prompt;

import org.springframework.stereotype.Component;

/**
 * @see https://www.llama.com/docs/model-cards-and-prompt-formats/llama4/
 */
@Component
public class Llama4PromptStrategy implements PromptStrategy {
	
	@Override
	public StringBuilder start(final String system, final String user) {
		final StringBuilder prompt = new StringBuilder();
		prompt.append("<|begin_of_text|>");
		if (!system.isEmpty()) {
			prompt.append("<|header_start|>system<|header_end|>\n\n");
			prompt.append(system);
			prompt.append("<|eot|>");
		}
		prompt.append("<|header_start|>user<|header_end|>\n\n");
		prompt.append(user);
		prompt.append("<|eot|><|header_start|>assistant<|header_end|>\n");
		
		return prompt;
	}
	
	@Override
	public void next(final StringBuilder prompt, final String assistant, final String user) {
		prompt.append(assistant);
		prompt.append("<|eot|><|header_start|>user<|header_end|>\n\n" + user + "<|eot|><|header_start|>assistant<|header_end|>\n");
	}
	
	@Override
	public String removeEOT(final String prompt) {
		return prompt.strip().replace("<|eot|>", "");
	}
	
	@Override
	public String getPromptName() {
		return "llama4";
	}
}
