package de.extio.lmlib.prompt;

import org.springframework.stereotype.Component;

@Component
public class MistralNemoPromptStrategy implements PromptStrategy {
	
	@Override
	public StringBuilder start(final String instruction, final String user) {
		final StringBuilder prompt = new StringBuilder();
		prompt.append("<s>[INST]");
		if (! instruction.isEmpty()) {
			prompt.append(instruction);
			prompt.append("\n");
		}
		prompt.append(user);
		prompt.append("[/INST]");
		return prompt;
	}
	
	@Override
	public void next(final StringBuilder prompt, final String assistant, final String user) {
		prompt.append(assistant);
		prompt.append("</s>[INST]");
		prompt.append(user);
		prompt.append("[/INST]");
	}
	
	@Override
	public String removeEOT(final String prompt) {
		return prompt.strip().replace("</s>", "").replace("<|im_end|>", "");
	}
	
	@Override
	public String getPromptName() {
		return "mistralNemo";
	}
	
}