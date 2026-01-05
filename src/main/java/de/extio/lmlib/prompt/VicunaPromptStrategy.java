package de.extio.lmlib.prompt;

public class VicunaPromptStrategy implements PromptStrategy {
	
	@Override
	public StringBuilder start(final String system, final String user) {
		final StringBuilder prompt = new StringBuilder();
		if (system.isEmpty()) {
			prompt.append(system);
			prompt.append("\n");
		}
		prompt.append("USER: ");
		prompt.append(user);
		prompt.append("\nASSISTANT:");
		return prompt;
	}
	
	@Override
	public void next(final StringBuilder prompt, final String assistant, final String user) {
		prompt.append(" ");
		prompt.append(assistant);
		prompt.append("</s>USER: ");
		prompt.append(user);
		prompt.append("\nASSISTANT:");
	}
	
	@Override
	public String removeEOT(final String prompt) {
		return prompt.strip().replace("</s>", "");
	}
	
	@Override
	public String getPromptName() {
		return "vicuna";
	}
	
}
