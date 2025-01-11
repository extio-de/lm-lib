package de.extio.lmlib.prompt;

import org.springframework.stereotype.Component;

@Component
public class Phi4PromptStrategy implements PromptStrategy {
	
	@Override
	public StringBuilder start(final String system, final String question, final String text) {
		final StringBuilder prompt = new StringBuilder();
		if (! system.isEmpty()) {
			prompt.append("<|im_start|>system<|im_sep|>\n");
			prompt.append(system);
			prompt.append("<|im_end|>\n");
		}
		prompt.append("<|im_start|>user<|im_sep|>\n");
		prompt.append(question);
		if (!question.isEmpty() && !text.isEmpty()) {
			prompt.append("\n");
		}
		prompt.append(text);
		prompt.append("<|im_end|>\n<|im_start|>assistant<|im_sep|>");
		return prompt;
	}
	
	@Override
	public void continue_(final StringBuilder prompt, final String assistant) {
		this.next(prompt, assistant, "Continue");
	}
	
	@Override
	public void next(final StringBuilder prompt, final String assistant, final String user) {
		prompt.append("\n");
		prompt.append(assistant);
		prompt.append("<|im_end|>\n<|im_start|>user<|im_sep|>\n");
		prompt.append(user);
		prompt.append("<|im_end|>\n<|im_start|>assistant<|im_sep|>");
	}
	
	@Override
	public String removeEOT(final String prompt) {
		return prompt.strip().replace("<|im_end|>", "");
	}
	
	@Override
	public String getPromptName() {
		return "phi4";
	}
}
