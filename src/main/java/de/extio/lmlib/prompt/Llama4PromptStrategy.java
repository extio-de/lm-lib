package de.extio.lmlib.prompt;

import org.springframework.stereotype.Component;

/**
 * @see https://llama.meta.com/docs/model-cards-and-prompt-formats/meta-llama-3
 */
@Component
public class Llama4PromptStrategy implements PromptStrategy {
	
	@Override
	public StringBuilder start(final String system, final String question, final String text) {
		final StringBuilder prompt = new StringBuilder();
		prompt.append("<|begin_of_text|>");
		if (!system.isEmpty()) {
			prompt.append("<|header_start|>system<|header_end|>\n\n");
			prompt.append(system);
			prompt.append("<|eot|>");
		}
		prompt.append("<|header_start|>user<|header_end|>\n\n");
		prompt.append(question);
		if (!question.isEmpty() && !text.isEmpty()) {
			prompt.append("\n\n");
		}
		prompt.append(text);
		prompt.append("<|eot|><|header_start|>assistant<|header_end|>\n");
		
		return prompt;
	}
	
	@Override
	public void continue_(final StringBuilder prompt, final String assistant) {
		this.next(prompt, assistant, "Continue");
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
