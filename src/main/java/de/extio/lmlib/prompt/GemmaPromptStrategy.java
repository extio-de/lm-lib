package de.extio.lmlib.prompt;

public class GemmaPromptStrategy implements PromptStrategy {
	
	@Override
	public StringBuilder start(final String system, final String user) {
		final StringBuilder prompt = new StringBuilder();
		prompt.append("<start_of_turn>user\n");
		if (!system.isEmpty()) {
			prompt.append(system);
			prompt.append("\n\n");
		}
		prompt.append(user);
		prompt.append("<end_of_turn>\n<start_of_turn>model\n");
		return prompt;
	}
	
	@Override
	public void next(final StringBuilder prompt, final String assistant, final String user) {
		prompt.append(assistant);
		prompt.append("<end_of_turn>\n<start_of_turn>user\n");
		prompt.append(user);
		prompt.append("<end_of_turn>\n<start_of_turn>model\n");
	}
	
	@Override
	public String removeEOT(final String prompt) {
		return prompt.strip().replace("<end_of_turn>", "");
	}
	
	@Override
	public String getPromptName() {
		return "gemma2,3";
	}
	
}
