package de.extio.lmlib.client.prompt;

import org.springframework.stereotype.Component;

@Component
public class Phi3PromptStrategy implements PromptStrategy {
	
	@Override
	public StringBuilder start(final String system, final String question, final String text) {
		final StringBuilder prompt = new StringBuilder();
		prompt.append("<|user|>\n");
		prompt.append(system);
		if (!system.isEmpty()) {
			prompt.append("\n");
		}
		prompt.append(question);
		if (!question.isEmpty() && !text.isEmpty()) {
			prompt.append("\n");
		}
		prompt.append(text);
		prompt.append(" <|end|>\n<|assistant|>");
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
		prompt.append("<|user|>\n");
		prompt.append(user);
		prompt.append("<|end|>\n<|assistant|>");
	}
	
	@Override
	public String removeEOT(final String prompt) {
		return prompt.strip().replace("<|end|>", "");
	}
	
	@Override
	public String getPromptName() {
		return "phi3";
	}
}
