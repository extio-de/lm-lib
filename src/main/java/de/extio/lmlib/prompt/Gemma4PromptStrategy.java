package de.extio.lmlib.prompt;

public class Gemma4PromptStrategy implements PromptStrategy {
	
	private static final String TURN_END = "<turn|>";
	private static final String THOUGHT_START = "<|channel>thought\n";
	private static final String THOUGHT_END = "\n<channel|>";
	private static final String SYSTEM_START = "<|turn>system\n";
	private static final String USER_START = "<|turn>user\n";
	private static final String MODEL_START = "<|turn>model\n";
	
	@Override
	public String getPromptName() {
		return "gemma4";
	}
	
	@Override
	public StringBuilder start(final String system, final String user) {
		final StringBuilder prompt = new StringBuilder();
		if (system != null && !system.isEmpty()) {
			prompt.append(SYSTEM_START);
			prompt.append(system);
			prompt.append(TURN_END).append("\n");
		}
		if (user != null && !user.isEmpty()) {
			prompt.append(USER_START);
			prompt.append(user);
			prompt.append(TURN_END).append("\n");
		}
		prompt.append(MODEL_START);
		return prompt;
	}
	
	@Override
	public void next(final StringBuilder prompt, final String assistant, final String user) {
		final String assistantContent = assistant == null ? "" : assistant;
		if (assistantContent.startsWith(MODEL_START) && this.endsWith(prompt, MODEL_START)) {
			prompt.setLength(prompt.length() - MODEL_START.length());
		}
		prompt.append(assistantContent);
		if (!assistantContent.endsWith(TURN_END)) {
			if (!assistantContent.endsWith("\n")) {
				prompt.append("\n");
			}
			prompt.append(TURN_END).append("\n");
		}
		else if (!assistantContent.endsWith(TURN_END + "\n")) {
			prompt.append("\n");
		}
		if (user != null && !user.isEmpty()) {
			prompt.append(USER_START);
			prompt.append(user);
			prompt.append(TURN_END).append("\n");
		}
		prompt.append(MODEL_START);
	}
	
	@Override
	public String removeEOT(final String prompt) {
		if (prompt == null) {
			return null;
		}
		return prompt.strip().replace(TURN_END, "");
	}
	
	@Override
	public String getResponse(final String prompt) {
		final String assistantBlock = this.assistantBlock(prompt);
		if (assistantBlock == null) {
			return null;
		}
		final int thoughtEnd = assistantBlock.indexOf(THOUGHT_END);
		if (thoughtEnd != -1) {
			return assistantBlock.substring(thoughtEnd + THOUGHT_END.length()).strip();
		}
		return assistantBlock.strip();
	}
	
	@Override
	public String getReasoning(final String prompt) {
		final String assistantBlock = this.assistantBlock(prompt);
		if (assistantBlock == null) {
			return null;
		}
		final int thoughtStart = assistantBlock.indexOf(THOUGHT_START);
		final int thoughtEnd = assistantBlock.indexOf(THOUGHT_END);
		if (thoughtStart != -1 && thoughtEnd > thoughtStart) {
			return assistantBlock.substring(thoughtStart + THOUGHT_START.length(), thoughtEnd).strip();
		}
		return null;
	}
	
	private String assistantBlock(final String prompt) {
		if (prompt == null) {
			return null;
		}
		String assistantBlock = prompt;
		final int lastAssistantStart = prompt.lastIndexOf(MODEL_START);
		if (lastAssistantStart != -1) {
			assistantBlock = prompt.substring(lastAssistantStart + MODEL_START.length());
		}
		final int turnEnd = assistantBlock.indexOf(TURN_END);
		if (turnEnd != -1) {
			assistantBlock = assistantBlock.substring(0, turnEnd);
		}
		return assistantBlock;
	}
	
	private boolean endsWith(final StringBuilder value, final String suffix) {
		if (value.length() < suffix.length()) {
			return false;
		}
		for (int i = 0; i < suffix.length(); i++) {
			if (value.charAt(value.length() - suffix.length() + i) != suffix.charAt(i)) {
				return false;
			}
		}
		return true;
	}
	
}