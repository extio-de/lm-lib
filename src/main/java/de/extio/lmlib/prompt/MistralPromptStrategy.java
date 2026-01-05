package de.extio.lmlib.prompt;

public class MistralPromptStrategy implements PromptStrategy {
	
	@Override
	public StringBuilder start(final String instruction, final String user) {
		final StringBuilder prompt = new StringBuilder();
		prompt.append("<s>[INST]");
		prompt.append(instruction);
		if (!instruction.isEmpty() && !user.isEmpty()) {
			prompt.append("\n");
		}
		prompt.append(user);
		prompt.append(" [/INST]");
		return prompt;
	}
	
	@Override
	public void next(final StringBuilder prompt, final String assistant, final String user) {
		prompt.append(" ");
		prompt.append(assistant);
		prompt.append("</s> [INST] ");
		prompt.append(user);
		prompt.append(" [/INST]");
	}
	
	@Override
	public String removeEOT(final String prompt) {
		return prompt.strip().replace("</s>", "").replace("<|im_end|>", "");
	}
	
	@Override
	public String getPromptName() {
		return "mistral";
	}
	
}