package de.extio.lmlib.prompt;

public class ChatMLPromptStrategy implements PromptStrategy {
	
	@Override
	public StringBuilder start(final String system, final String user) {
		final StringBuilder prompt = new StringBuilder();
		if (!system.isEmpty()) {
			prompt.append("<|im_start|>system\n");
			prompt.append(system);
			prompt.append("<|im_end|>\n");
		}
		prompt.append("<|im_start|>user\n");
		prompt.append(user);
		prompt.append("<|im_end|>\n<|im_start|>assistant\n");
		return prompt;
	}
	
	@Override
	public void next(final StringBuilder prompt, final String assistant, final String user) {
		prompt.append(assistant);
		prompt.append("<|im_end|>\n<|im_start|>user\n");
		prompt.append(user);
		prompt.append("<|im_end|>\n<|im_start|>assistant\n");
	}
	
	@Override
	public String removeEOT(final String prompt) {
		return prompt.strip().replace("<|im_end|>", "");
	}
	
	@Override
	public String getPromptName() {
		return "chatml";
	}
}
