package de.extio.lmlib.prompt;

public class AlpacaShortPromptStrategy implements PromptStrategy {
	
	@Override
	public StringBuilder start(final String instruction, final String user) {
		final StringBuilder prompt = new StringBuilder();
		if (! instruction.isEmpty()) {
			prompt.append("### Instruction:\n");
			prompt.append(instruction);
			prompt.append("\n\n");
		}
		prompt.append("### Input:\n");
		prompt.append(user);
		prompt.append("\n\n### Response:\n");
		return prompt;
	}
	
	@Override
	public void next(final StringBuilder prompt, final String assistant, final String user) {
		prompt.append(assistant);
		prompt.append("\n\n### Input:\n");
		prompt.append(user);
		prompt.append("\n\n### Response:\n");
	}
	
	@Override
	public String removeEOT(final String prompt) {
		return prompt.strip().replace("</s>", "");
	}
	
	@Override
	public String getPromptName() {
		return "alpaca";
	}
	
}
